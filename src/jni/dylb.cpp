#if 0 // My IDE is buggy

#include <map>
#include <string>
#include <vector>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>

// Заглушки типов для компиляции вне main.cpp
struct mach_header { uint32_t magic; uint32_t cputype; uint32_t cpusubtype; uint32_t filetype; uint32_t ncmds; uint32_t sizeofcmds; uint32_t flags; };
struct load_command { uint32_t cmd; uint32_t cmdsize; };
struct segment_command { uint32_t cmd; uint32_t cmdsize; char segname[16]; uint32_t vmaddr; uint32_t vmsize; uint32_t fileoff; uint32_t filesize; uint32_t maxprot; uint32_t initprot; uint32_t nsects; uint32_t flags; };
struct section { char sectname[16]; char segname[16]; uint32_t addr; uint32_t size; uint32_t offset; uint32_t align; uint32_t reloff; uint32_t nreloc; uint32_t flags; uint32_t reserved1; uint32_t reserved2; };
struct symtab_command { uint32_t cmd; uint32_t cmdsize; uint32_t symoff; uint32_t nsyms; uint32_t stroff; uint32_t strsize; };
struct dyld_info_command { uint32_t cmd; uint32_t cmdsize; uint32_t rebase_off; uint32_t rebase_size; uint32_t bind_off; uint32_t bind_size; uint32_t weak_bind_off; uint32_t weak_bind_size; uint32_t lazy_bind_off; uint32_t lazy_bind_size; uint32_t export_off; uint32_t export_size; };
struct nlist { union { uint32_t n_strx; } n_un; uint8_t n_type; uint8_t n_sect; int16_t n_desc; uint32_t n_value; };
struct fat_arch { uint32_t cputype; uint32_t cpusubtype; uint32_t offset; uint32_t size; uint32_t align; };

extern std::string g_workDir;
extern void LogToJava(const std::string& msg);
extern void ProcessBindOpcodes(int fd, uint32_t arch_offset, uint32_t bind_off, uint32_t bind_size, const std::vector<segment_command>& segments, uint32_t slide);

std::map<std::string, bool> g_loadedDylibs;
std::map<std::string, uint32_t> g_dylibSymbols;
std::map<std::string, bool> g_implementedDylibSymbols;

struct dylib_command { uint32_t cmd; uint32_t cmdsize; uint32_t name_offset; uint32_t timestamp; uint32_t current_version; uint32_t compatibility_version; };

void LoadDylib(const std::string& dylibName) {
    if (g_loadedDylibs[dylibName]) return;
    g_loadedDylibs[dylibName] = true;
    
    std::string dylibPath = g_workDir + "dylibs/" + dylibName;
    int fd = open(dylibPath.c_str(), O_RDONLY);
    if (fd < 0) { LogToJava("HLE: Dylib not found: " + dylibName); return; }
    
    uint32_t magic; read(fd, &magic, sizeof(magic));
    uint32_t arch_offset = 0; bool hasArmv7 = false; bool hasArmv6 = false;
    
    if (magic == 0xbebafeca || magic == 0xcafebabe) {
        uint32_t nfat; read(fd, &nfat, sizeof(nfat)); nfat = __builtin_bswap32(nfat);
        for (uint32_t i = 0; i < nfat; i++) {
            fat_arch fa; read(fd, &fa, sizeof(fa));
            uint32_t type = __builtin_bswap32(fa.cputype); uint32_t subtype = __builtin_bswap32(fa.cpusubtype);
            if (type == 12) {
                if (subtype == 9) { hasArmv7 = true; arch_offset = __builtin_bswap32(fa.offset); }
                else if (subtype == 6 && !hasArmv7) { hasArmv6 = true; arch_offset = __builtin_bswap32(fa.offset); }
            }
        }
    } else if (magic == 0xfeedface || magic == 0xcefaedfe) {
        mach_header mh; lseek(fd, 0, SEEK_SET); read(fd, &mh, sizeof(mh));
        if (mh.cputype == 12) arch_offset = 0;
    }
    
    lseek(fd, arch_offset, SEEK_SET); mach_header mh; read(fd, &mh, sizeof(mh));
    
    // Рассчитываем сдвиг (slide), если библиотека нормализована к 0x0
    uint32_t dylib_slide = 0;
    uint32_t temp_offset = arch_offset + sizeof(mach_header);
    for (uint32_t i = 0; i < mh.ncmds; i++) {
        load_command lc; lseek(fd, temp_offset, SEEK_SET); read(fd, &lc, sizeof(lc));
        if (lc.cmd == 1) { // LC_SEGMENT
            segment_command seg; lseek(fd, temp_offset, SEEK_SET); read(fd, &seg, sizeof(seg));
            if (seg.vmaddr < 0x10000000) {
                static uint32_t global_slide = 0x60000000;
                dylib_slide = global_slide;
                global_slide += 0x04000000; // Шаг в 64MB для каждой библиотеки
            }
            break;
        }
        temp_offset += lc.cmdsize;
    }

    uint32_t cmd_offset = arch_offset + sizeof(mach_header);
    symtab_command symtab = {0};
    uint32_t dyld_bind_off = 0, dyld_bind_size = 0;
    uint32_t dyld_lazy_bind_off = 0, dyld_lazy_bind_size = 0;
    std::vector<segment_command> dylib_segments;
    
    for (uint32_t i = 0; i < mh.ncmds; i++) {
        lseek(fd, cmd_offset, SEEK_SET); load_command lc; read(fd, &lc, sizeof(lc));
        if (lc.cmd == 1) { 
            segment_command seg; lseek(fd, cmd_offset, SEEK_SET); read(fd, &seg, sizeof(seg));
            dylib_segments.push_back(seg);
            if (seg.vmsize > 0) {
                uint32_t target_addr = seg.vmaddr + dylib_slide;
                void* mapped = mmap((void*)target_addr, seg.vmsize, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_FIXED | MAP_ANONYMOUS, -1, 0);
                if (mapped != MAP_FAILED && seg.filesize > 0) { lseek(fd, arch_offset + seg.fileoff, SEEK_SET); read(fd, mapped, seg.filesize); }
            }
        } else if (lc.cmd == 2) { lseek(fd, cmd_offset, SEEK_SET); read(fd, &symtab, sizeof(symtab)); }
        else if (lc.cmd == 0x22 || lc.cmd == 0x80000022) { dyld_info_command dyld; lseek(fd, cmd_offset, SEEK_SET); read(fd, &dyld, sizeof(dyld)); dyld_bind_off = dyld.bind_off; dyld_bind_size = dyld.bind_size; dyld_lazy_bind_off = dyld.lazy_bind_off; dyld_lazy_bind_size = dyld.lazy_bind_size; }
        cmd_offset += lc.cmdsize;
    }

    if (dylib_slide > 0) {
        uint32_t dylib_min_vmaddr = 0xFFFFFFFF; uint32_t dylib_max_vmaddr = 0;
        for (const auto& seg : dylib_segments) {
            if (seg.vmsize > 0) {
                if (seg.vmaddr < dylib_min_vmaddr) dylib_min_vmaddr = seg.vmaddr;
                if (seg.vmaddr + seg.vmsize > dylib_max_vmaddr) dylib_max_vmaddr = seg.vmaddr + seg.vmsize;
            }
        }
        uint32_t scan_cmd_offset = arch_offset + sizeof(mach_header);
        for (uint32_t i = 0; i < mh.ncmds; i++) {
            load_command lc; lseek(fd, scan_cmd_offset, SEEK_SET); read(fd, &lc, sizeof(lc));
            if (lc.cmd == 1) {
                segment_command seg; lseek(fd, scan_cmd_offset, SEEK_SET); read(fd, &seg, sizeof(seg));
                uint32_t sect_offset = scan_cmd_offset + sizeof(segment_command);
                for(uint32_t s = 0; s < seg.nsects; s++) {
                    section sect; lseek(fd, sect_offset, SEEK_SET); read(fd, &sect, sizeof(sect));
                    std::string sectname = sect.sectname;
                    bool isCode = (sectname == "__text" || sectname == "__symbol_stub" || sectname == "__stub_helper" || sectname == "__picsymbolstub");
                    bool isString = (sectname == "__cstring" || sectname == "__objc_methname" || sectname == "__objc_classname" || sectname == "__objc_methtype");
                    if (!isCode && !isString && sect.size > 0) {
                        uint32_t* ptr = (uint32_t*)(sect.addr + dylib_slide);
                        uint32_t count = sect.size / 4;
                        for (uint32_t j = 0; j < count; j++) {
                            uint32_t val = ptr[j];
                            if (val >= dylib_min_vmaddr && val < dylib_max_vmaddr && val > 0x1000) {
                                ptr[j] = val + dylib_slide;
                            }
                        }
                    }
                    sect_offset += sizeof(section);
                }
            }
            scan_cmd_offset += lc.cmdsize;
        }
    }

    if (dyld_bind_size > 0) ProcessBindOpcodes(fd, arch_offset, dyld_bind_off, dyld_bind_size, dylib_segments, dylib_slide);
    if (dyld_lazy_bind_size > 0) ProcessBindOpcodes(fd, arch_offset, dyld_lazy_bind_off, dyld_lazy_bind_size, dylib_segments, dylib_slide);
    
    if (symtab.cmdsize > 0) {
        std::vector<char> strTable(symtab.strsize); lseek(fd, arch_offset + symtab.stroff, SEEK_SET); read(fd, strTable.data(), symtab.strsize);
        std::vector<nlist> symTable(symtab.nsyms); lseek(fd, arch_offset + symtab.symoff, SEEK_SET); read(fd, symTable.data(), symtab.nsyms * sizeof(nlist));
        for (uint32_t i = 0; i < symtab.nsyms; i++) {
            uint8_t type = symTable[i].n_type;
            if ((type & 0x01) && (type & 0x0E)) {
                if (symTable[i].n_un.n_strx > 0 && symTable[i].n_value > 0) {
                    std::string symName = &strTable[symTable[i].n_un.n_strx];
                    g_dylibSymbols[symName] = symTable[i].n_value + dylib_slide;
                }
            }
        }
    }
    close(fd);
    
    std::string slideLog = dylib_slide > 0 ? " (Slid to 0x" + std::to_string(dylib_slide) + ")" : "";
    LogToJava("HLE: Loaded dyld dependency " + dylibName + slideLog);
}

#endif
