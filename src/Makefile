ANT_MAKE	:=no #yes
SCRIPT_LANGUATE	?= javascript #jython
BASE_DIR	:= .
SOURCE_DIR	:= ./java
OUTPUT_DIR	?= ../bin
ALL_JAVAS	:= $(shell find ./$(SOURCE_DIR) -name '*.java')
SCRIPT_DIR_BASE ?= ../script
LIBREADLINE_DIR	= ../libreadline-java-0.8.0

ifeq ($(SCRIPT_LANGUAGE), jython)
SCRIPT_LIB	:= jython-2.5.3.jar
else
SCRIPT_LIB	:= js.jar 
endif


ifeq ($(SCRIPT_LANGUAGE), jython)
EXTCLUDE_FILES	:= **/JavaScript*.java 
ALL_JAVAS	:= $(filter-out %/JavaScriptManager.java,$(ALL_JAVAS))
ALL_JAVAS	:= $(filter-out %/JavaScriptObject.java,$(ALL_JAVAS))
SCRIPT_DIR_NAME	:=py
SCRIPT_SUFFIX	:=py
else
EXTCLUDE_FILES	:= **/Jython*.java 
ALL_JAVAS	:= $(filter-out %/JythonManager.java,$(ALL_JAVAS))
ALL_JAVAS	:= $(filter-out %/JythonObject.java,$(ALL_JAVAS))
SCRIPT_DIR_NAME	:=js
SCRIPT_SUFFIX	:=js
endif

ALL_CLASSES	:= $(addprefix $(OUTPUT_DIR),		\
			$(patsubst ./$(SOURCE_DIR)/%,/%,		\
			  $(ALL_JAVAS:.java=.class)))

ALL_SCRIPT_FILES := $(shell find $(SCRIPT_DIR_BASE)/$(SCRIPT_DIR_NAME) -name '*.$(SCRIPT_SUFFIX)')
ALL_SCRIPT_TARGET := $(addprefix $(OUTPUT_DIR),		\
			$(patsubst $(SCRIPT_DIR_BASE)/%,/%,		\
			  $(ALL_SCRIPT_FILES)))

# Linux tools
FIND	:= /usr/bin/find
MKDIR	:= mkdir -p
RM	:= rm -rf

#Java tools
JAVA		:= java
JAVAC		:= javac
ANT		:= ant

ifdef COMSPEC
CLASS_PATH	:= $(OUTPUT_DIR)\;../lib/tools.jar\;../libreadline-java-0.8.0/build\;../lib/$(SCRIPT_LIB)
else
CLASS_PATH	:= $(OUTPUT_DIR):../lib/tools.jar:../libreadline-java-0.8.0/build:../lib/$(SCRIPT_LIB)
endif

JFLAGS		:= -sourcepath $(SOURCE_DIR) 	\
	   	   -d $(OUTPUT_DIR)		\
	   	   -g 				\
	   	   -classpath $(CLASS_PATH)	


.PHONY: all clean create_dir check_script
all : libreadline_java create_dir check_script $(ALL_CLASSES) $(ALL_SCRIPT_TARGET)

libreadline_java : 
	@if [ ! -f $(LIBREADLINE_DIR)/build/org/gnu/readline/Readline.class ]; then \
	make -C $(LIBREADLINE_DIR) build-java build-native; \
	fi

create_dir :
	@if [ ! -d $(OUTPUT_DIR) ]; then \
	mkdir -p $(OUTPUT_DIR); \
	cp -rf $(SCRIPT_DIR_BASE)/$(SCRIPT_DIR_NAME) $(OUTPUT_DIR); \
	fi

check_script :
ifeq ($(SCRIPT_LANGUAGE), jython)
	# Use script: jython
	@rm -f $(OUTPUT_DIR)/com/sun/tools/example/debug/tty/JavaScriptManager*.class \
	$(OUTPUT_DIR)/com/sun/tools/example/debug/tty/JavaScriptObject.class 
	@rm -rf $(OUTPUT_DIR)/js
else
	# Use script: javascript
	@rm -f $(OUTPUT_DIR)/com/sun/tools/example/debug/tty/JythonManager*.class \
	$(OUTPUT_DIR)/com/sun/tools/example/debug/tty/JythonObject.class 
	@rm -rf $(OUTPUT_DIR)/py
endif
	@if [ ! -d $(OUTPUT_DIR)/$(SCRIPT_DIR_NAME) ]; then \
	cp -arvf $(SCRIPT_DIR_BASE)/$(SCRIPT_DIR_NAME) $(OUTPUT_DIR); \
	fi


ifeq ($(ANT_MAKE), yes)
$(OUTPUT_DIR)/%.class : ./$(SOURCE_DIR)/%.java
	@$(ANT) -f ./build.xml -Dbasedir=$(BASE_DIR) -Ddir.classes=$(CLASS_PATH) \
	-Ddir.build=$(OUTPUT_DIR) -Ddir.src=$(SOURCE_DIR)
else
$(OUTPUT_DIR)/%.class : ./$(SOURCE_DIR)/%.java
	$(JAVAC) $(JFLAGS) $<
endif

	
$(OUTPUT_DIR)/%.$(SCRIPT_SUFFIX) : $(SCRIPT_DIR_BASE)/%.$(SCRIPT_SUFFIX)	
	@cp -vf $^ $@


clean:
	if [ -d $(OUTPUT_DIR) ]; then \
	rm -rf $(OUTPUT_DIR); \
	fi
