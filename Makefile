#prerequisite: apt-get install libreadline6-dev

LIBREADLINE_DIR	:= ./libreadline-java-0.8.0
LIBREADLINE_OUTPUT_DIR := $(LIBREADLINE_DIR)/build
DEMO_DIR := ./demo 
JDB_DIR		:= ./src
JDB_OUTPUT_DIR	:= ./bin
LIBREADLINE_SO 	:= $(LIBREADLINE_DIR)/libJavaReadline.so
MF_FILE		:= ./resources/manifest.mf
DIST_DIR	:= ./dist
export JAVA_HOME ?= /usr/lib/jvm/java-6-sun

SCRIPT_LANGUAGE	:= javascript #jython
export SCRIPT_LANGUAGE

ifdef COMSPEC
LIBREADLINE_SO 	:= $(LIBREADLINE_DIR)/JavaReadline.dll $(LIBREADLINE_DIR)/win32_lib/readline-5.0-1-bin/bin/*.dll
endif

prefix?=/usr/lib/xjdb


.PHONY: all jdb libreadline_java clean dist test demo install 
all : jdb demo

jdb : libreadline_java
	@make -C $(JDB_DIR) OUTPUT_DIR=../$(JDB_OUTPUT_DIR)

libreadline_java : 
	@make -C $(LIBREADLINE_DIR) build-java build-native

install :dist
	@if [ -d $(prefix) ]; then \
	rm -rf $(prefix)/*; \
	fi; \
	mkdir -p $(prefix); \
	cp -arvf $(DIST_DIR)/* $(prefix); \
	strip $(prefix)/libJavaReadline.so

dist : jdb 
	@mkdir -p $(DIST_DIR); \
	rm -rf $(DIST_DIR)/*; \
	cp ./lib/tools.jar $(DIST_DIR); \
	cd $(DIST_DIR); \
	jar xf tools.jar; \
	rm tools.jar; \
	cd ..
	cp -arvf $(JDB_OUTPUT_DIR)/* $(DIST_DIR); \
	cp -arvf $(LIBREADLINE_OUTPUT_DIR)/org $(DIST_DIR); \
	cd $(DIST_DIR); \
	jar cfm xjdb.jar ../$(MF_FILE) *; \
	chmod 755 xjdb.jar; \
	rm -rf com; \
	rm -rf sun; \
	rm -rf org; \
	rm -rf js; \
	rm -rf py; \
	rm -rf META-INF; \
	cd ..; \
	cp -vf $(LIBREADLINE_SO) $(DIST_DIR); \
	strip ./$(DIST_DIR)/libJavaReadline.so; \
	cp -vf ./resources/xjdb $(DIST_DIR); 
ifeq ($(SCRIPT_LANGUAGE), jython)
	@cp -vf ./lib/jython-2.5.3.jar $(DIST_DIR); 
	@tar xvf ./lib/jython_Lib.tar.bz2 -C $(DIST_DIR)
else
	@cp -vf ./lib/js.jar $(DIST_DIR); 
endif

demo:
	@make -C $(DEMO_DIR) 

test:
	@cd ./$(DEMO_DIR); \
	../dist/xjdb -classpath ./bin -sourcepath ./ProducerConsumer:./XJdbTest XJdbTest #ProducerConsumer

clean:
	rm -rf $(DIST_DIR)
	@make -C $(JDB_DIR) clean
	@make -C $(LIBREADLINE_DIR) clean
	@make -C $(DEMO_DIR) clean

