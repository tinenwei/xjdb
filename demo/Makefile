#usage:
# OUTPUT_DIR for classes path
# CLASS_PATH for library path
# SOURCE_DIR for sources path and paths are concatened by ':'

#Location of trees.
ANT_MAKE	:=no
BASE_DIR	:= .
SOURCE_DIR	:= ./XJdbTest
OUTPUT_DIR	:= ./bin
#JAVA_DIR	:= ./src/main/java
SOURCE_DIR_LIST := $(subst :, ,$(SOURCE_DIR))
ALL_JAVAS	:= $(foreach i,$(SOURCE_DIR_LIST), $(shell cd $(i);find . -name '*.java' ! -name 'package-info.java'))
ALL_CLASSES	:= $(addprefix $(OUTPUT_DIR),		\
			$(patsubst ./%,/%,		\
			  $(ALL_JAVAS:.java=.class)))

# Linux tools
FIND	:= /usr/bin/find
MKDIR	:= mkdir -p
RM	:= rm -rf

# Java tools
JAVA		:= java
JAVAC		:= javac

# concatenate jar
CLASS_PATH	?= $(OUTPUT_DIR)

JFLAGS		:= -sourcepath $(SOURCE_DIR)	\
	   	   -d $(OUTPUT_DIR)		\
		   -g 				\
	   	   -classpath $(CLASS_PATH)	




.PHONY: all clean create_dir copy_jdbrc
all : create_dir $(ALL_CLASSES)

create_dir :
	@if [ ! -d $(OUTPUT_DIR) ]; then \
	mkdir -p $(OUTPUT_DIR); \
	fi

define java_build
ifeq ($(ANT_MAKE),yes) 
$(OUTPUT_DIR)/%.class : $(1)/%.java
	@ant -f ${J_ANT_BUILD_FILE} \
	-Dbasedir=$(BASE_DIR) \
	-Ddir.classes=$(CLASS_PATH) \
	-Ddir.build=$(OUTPUT_DIR) \
	-Ddir.src=$(SOURCE_DIR)	
else	
$(OUTPUT_DIR)/%.class : $(1)/%.java	
	$$(JAVAC) $$(JFLAGS) $$< 
endif
endef

$(foreach i,$(SOURCE_DIR_LIST), $(eval $(call java_build,$(i))))


clean:
	if [ -d $(OUTPUT_DIR) ]; then \
	rm -rf $(OUTPUT_DIR); \
	fi


