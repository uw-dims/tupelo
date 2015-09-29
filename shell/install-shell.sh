#!/bin/bash
set -e

INSTALL_BASE="/opt/tupelo"

# If there was a path specified, use that instead of the default
if [ ! "$1" == "" ]; then
   INSTALL_BASE="$1"
fi

echo "Installing to $INSTALL_BASE"

if [ -d "$INSTALL_BASE" ]; then
   echo "Previous install detected, removing"
   rm -rf "$INSTALL_BASE"
fi

# Copy all the jars and properties into the lib folder
mkdir -p "$INSTALL_BASE/lib"

cp target/*.jar "$INSTALL_BASE/lib"
# Copy any properties, ignoring errors
cp target/*.properties "$INSTALL_BASE/lib" 2> /dev/null || :

cp *.properties "$INSTALL_BASE/lib" 2> /dev/null || :

# Copy the elvis script into the bin folder
mkdir -p "$INSTALL_BASE/bin"
cp elvis "$INSTALL_BASE/bin"

# Set permissions
chmod -R a+r "$INSTALL_BASE"
chmod -R a-w "$INSTALL_BASE"

# Make sure the directories have +x
chmod -R a+x "$INSTALL_BASE/bin"
chmod a+x "$INSTALL_BASE/lib"

echo "Installed to $INSTALL_BASE"
echo "Add '$INSTALL_BASE/bin' to your path"

