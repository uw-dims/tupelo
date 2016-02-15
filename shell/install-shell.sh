#!/bin/bash
set -e

INSTALL_BASE="/opt/dims"

# If there was a path specified, use that instead of the default
if [ ! "$1" == "" ]; then
   INSTALL_BASE="$1"
fi

JAR_PATH="$INSTALL_BASE/jars/tupelo"

echo "Installing to $INSTALL_BASE"

if [ -d "$JAR_PATH" ]; then
    echo "Existing jar path found, cleaning"
    rm -f $JAR_PATH/*
fi

# Copy all the jars and properties into the lib folder
mkdir -p "$JAR_PATH"

cp target/*.jar "$JAR_PATH"
# Copy any properties, ignoring errors
cp target/*.properties "$JAR_PATH" 2> /dev/null || :

cp *.properties "$JAR_PATH" 2> /dev/null || :

# Copy the elvis script into the bin folder
mkdir -p "$INSTALL_BASE/bin"
cp elvis "$INSTALL_BASE/bin"

# Set permissions
# Don't use recursive changes, be explicit
# Make sure the directories have +rx
chmod a+rx "$INSTALL_BASE"
chmod a+rx "$INSTALL_BASE/bin"
chmod a+rx "$JAR_PATH/../"
chmod a+rx "$JAR_PATH"
# Make sure the elvis script has +x
chmod a+x "$INSTALL_BASE/bin/elvis"

echo "Installed to $INSTALL_BASE"
echo "Add '$INSTALL_BASE/bin' to your path"

