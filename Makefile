# Makefile for installing Tupelo components into the /opt/dims
# filesystem structure.  Components here means 
# 
# 1: Java JAR files
#
# 2: Shell scripts driving those JARs.

SHELL=/bin/bash

include $(DIMS)/etc/Makefile.dims.global

JARDIR=$(DIMS)/jars/tupelo

BINDIR=$(DIMS)/bin

OWNER = dims
GROUP = dims
MODE  = 755

INSTALL=install -g $(GROUP) -o $(OWNER) -m $(MODE)

#HELP vars - show variables used in this Makefile
.PHONY: vars
vars:
	@echo JARDIR  is $(JARDIR)
	@echo BINDIR  is $(BINDIR)
	@echo OWNER   is $(OWNER)
	@echo GROUP   is $(GROUP)
	@echo MODE    is $(MODE)
	@echo INSTALL is $(INSTALL)

#HELP install - install jars and binaries
.PHONY: install
install: install-jars install-bin

#HELP install-jars - install jar files
.PHONY: install-jars
install-jars: package installdirs
	@$(INSTALL) shell/target/*.jar $(JARDIR)
	@$(INSTALL) shell/target/*.properties $(JARDIR)
	@$(INSTALL) shell/*.properties $(JARDIR)

#HELP install-bin - install elvis shell
.PHONY: install-bin
install-bin: installdirs
	@$(INSTALL) shell/elvis $(BINDIR)

#HELP package - build maven package
.PHONY: package
package: properties
	mvn package

#HELP properties - make sure filter.properties files exist where needed
.PHONY: properties
properties: $(GIT)/tupelo/amqp/server/filter.properties \
	    $(GIT)/tupelo/amqp/client/filter.properties \
	    $(GIT)/tupelo/http/server/filter.properties

$(GIT)/tupelo/amqp/client/filter.properties:
	touch $(GIT)/tupelo/amqp/client/filter.properties

$(GIT)/tupelo/amqp/server/filter.properties:
	touch $(GIT)/tupelo/amqp/server/filter.properties

$(GIT)/tupelo/http/server/filter.properties:
	touch $(GIT)/tupelo/http/server/filter.properties

#HLEP installdirs - create directories for installation
.PHONY: installdirs
installdirs:
	[ -d $(JARDIR) ] || \
	(mkdir -p $(JARDIR); \
	chown dims:dims $(JARDIR); \
	chmod 755 $(JARDIR); \
	echo "Created $(JARDIR) (dims:dims, mode 755)")
	[ -d $(BINDIR) ] || \
	(mkdir -p $(BINDIR); \
	chown dims:dims $(BINDIR); \
	chmod 755 $(BINDIR); \
	echo "Created $(BINDIR) (dims:dims, mode 755)")

# eof
