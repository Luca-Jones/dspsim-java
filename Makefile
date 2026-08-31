.PHONY: default run jar test

BIN=bin
LIB=lib
SRC=src
MAIN=Main
OUT=dspsim

TESTBIN=bintest
TESTLIB=libtest
TESTSRC=test

JARS=$(shell find $(LIB) -name "*.jar" 2>/dev/null)
LIBS_COLON=$(subst $() ,:,$(JARS))
LIBS_SPACE=$(JARS)

# ponytail: test jars live outside $(LIB) so they never reach the shipped manifest
TESTJARS=$(shell find $(TESTLIB) -name "*.jar" 2>/dev/null)
TESTLIBS_COLON=$(subst $() ,:,$(TESTJARS))
TESTSOURCES=$(shell find $(TESTSRC) -name "*.java" 2>/dev/null)


.project:
	printf '%s\n' \
		'<?xml version="1.0" encoding="UTF-8"?>' \
		'<projectDescription>' \
		'	<name>$(OUT)</name>' \
		'	<buildSpec>' \
		'		<buildCommand>' \
		'			<name>org.eclipse.jdt.core.javabuilder</name>' \
		'		</buildCommand>' \
		'	</buildSpec>' \
		'	<natures>' \
		'		<nature>org.eclipse.jdt.core.javanature</nature>' \
		'	</natures>' \
		'</projectDescription>' > .project

.classpath: $(JARS) $(TESTJARS)
	printf '%s\n' \
		'<?xml version="1.0" encoding="UTF-8"?>' \
		'<classpath>' \
		'	<classpathentry kind="src" path="$(SRC)"/>' \
		$(if $(TESTSOURCES),'	<classpathentry kind="src" path="$(TESTSRC)" output="$(TESTBIN)"/>') \
		'	<classpathentry kind="output" path="$(BIN)"/>' \
		'	<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>' \
		$(foreach jar,$(JARS),'	<classpathentry kind="lib" path="$(jar)"/>') \
		$(foreach jar,$(TESTJARS),'	<classpathentry kind="lib" path="$(jar)"/>') \
		'</classpath>' > .classpath

default: .project .classpath
	javac --source-path $(SRC) -d $(BIN) $(SRC)/$(MAIN).java $(if $(LIBS_COLON),--class-path $(LIBS_COLON))

run:
	java --class-path $(BIN)$(if $(LIBS_COLON),:$(LIBS_COLON)) $(MAIN)

test: default
	javac --source-path $(TESTSRC) -d $(TESTBIN) $(TESTSOURCES) \
		--class-path $(BIN):$(TESTLIBS_COLON)$(if $(LIBS_COLON),:$(LIBS_COLON))
	java -jar $(TESTLIB)/junit-platform-console-standalone-*.jar execute \
		--class-path $(BIN):$(TESTBIN)$(if $(LIBS_COLON),:$(LIBS_COLON)) \
		--scan-class-path $(TESTBIN)

manifest.txt:
	printf "Main-Class: $(MAIN)\n$(if $(LIBS_SPACE),Class-Path: $(LIBS_SPACE)\n)" > manifest.txt

jar: default manifest.txt
	jar cfm $(OUT).jar manifest.txt -C $(BIN) .
	rm manifest.txt
