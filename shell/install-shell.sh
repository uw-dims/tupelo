#!/bin/bash
set -e

INSTALL_BASE="/opt/dims"
JAR_PATH="$INSTALL_BASE/jars/tupelo"

# If there was a path specified, use that instead of the default
if [ ! "$1" == "" ]; then
   INSTALL_BASE="$1"
fi

echo "Installing to $INSTALL_BASE"

if [ -d "JAR_PATH" ]; then
    echo "Existing jar path found, removing"
    rm -rf "$JAR_PATH"
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
chmod -R a+r "$INSTALL_BASE"
chmod -R a-w "$INSTALL_BASE"

# Make sure the directories have +x
chmod -R a+x "$INSTALL_BASE/bin"
chmod a+x "$JAR_PATH/../"
chmod a+x "$JAR_PATH"

echo "Installed to $INSTALL_BASE"
echo "Add '$INSTALL_BASE/bin' to your path"

