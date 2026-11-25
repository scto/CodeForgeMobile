LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := tomaslib
LOCAL_SRC_FILES := tomaslib.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)