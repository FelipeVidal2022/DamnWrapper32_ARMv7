LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := DamnWrapper32
LOCAL_SRC_FILES := main.cpp dylb.cpp sqlite3.c
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv2 -lz
include $(BUILD_SHARED_LIBRARY)
