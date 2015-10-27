#!/bin/bash
set -e

# Original ISO file
ISO=""
# Base work directory
# Everything will be done in a subfolder under this folder
WORK_DIR="/tmp/"

# The live user
# This user will be added to the drive group
USER="caine"

# Output file name
OUTFILE=""

# Extra files to include in the final filesystem
INCLUDE_DIR=""

show_help() {
cat << EOF
Usage: ${0##*/} [-h] [-u USER] [-w DIR] [-i DIR] ISOFILE OUTFILE
Repack the source ISOFILE with the tupelo shell (elvis)
Create the final OUTFILE ISO when done

    -h          display this help and exit
    -u USER     the live environment user (Default 'caine')
    -w DIR      the base work directory (Default /tmp)
    -i DIR      root of file tree to include in the final filesystem.
                This will be copied as-is into the FS, so make sure that
                the permissions are correct, and that everything is in the
                correct subfolder.
                NOTE: The file owner will be changed to root.
EOF
}

# Parse the commandline opts
while :; do
    case $1 in
        -h|-\?|--help)   # Call a "show_help" function to display a synopsis, then exit.
            show_help
            exit
            ;;
# User
        -u|--user)       # Takes an option argument, ensuring it has been specified.
            if [ -n "$2" ]; then
                USER=$2
                shift 2
                continue
            else
                printf 'ERROR: "--user" requires a non-empty option argument.\n' >&2
                exit 1
            fi
            ;;
        --user=?*)
            USER=${1#*=} # Delete everything up to "=" and assign the remainder.
            ;;
        --user=)         # Handle the case of an empty --file=
            printf 'ERROR: "--user" requires a non-empty option argument.\n' >&2
            exit 1
            ;;
# Work dir
        -w|--work)       # Takes an option argument, ensuring it has been specified.
            if [ -n "$2" ]; then
                WORK_DIR=$2
                shift 2
                continue
            else
                printf 'ERROR: "--work" requires a non-empty option argument.\n' >&2
                exit 1
            fi
            ;;
        --work=?*)
            WORK_DIR=${1#*=} # Delete everything up to "=" and assign the remainder.
            ;;
        --work=)         # Handle the case of an empty --file=
            printf 'ERROR: "--work" requires a non-empty option argument.\n' >&2
            exit 1
            ;;
# Include dir
        -i|--include)       # Takes an option argument, ensuring it has been specified.
            if [ -n "$2" ]; then
                INCLUDE_DIR=$2
                shift 2
                continue
            else
                printf 'ERROR: "--include" requires a non-empty option argument.\n' >&2
                exit 1
            fi
            ;;
        --include=?*)
            INCLUDE_DIR=${1#*=} # Delete everything up to "=" and assign the remainder.
            ;;
        --include=)         # Handle the case of an empty --file=
            printf 'ERROR: "--include" requires a non-empty option argument.\n' >&2
            exit 1
            ;;
# Else
        --)              # End of all options.
            shift
            break
            ;;
        -?*)
            printf 'WARN: Unknown option (ignored): %s\n' "$1" >&2
            ;;
        *)               # Default case: If no more options then break out of the loop.
            break
    esac

    shift
done

ISO=$1
OUTFILE=$2

# Verify that ISO and OUTFILE are set
if [ "$ISO" == "" ]; then
	echo "The source ISO must be defined"
	show_help
	exit 1
fi
if [ "$OUTFILE" == "" ]; then
	echo "The output file must be defined"
	show_help
	exit 1
fi
# Check if the source file exists
if [ ! -f "$ISO" ]; then
	echo "Source $ISO not found"
	show_help
	exit 1
fi

echo "----------"
echo "Source ISO:  $ISO"
echo "User:        $USER"
echo "Work Dir:    $WORK_DIR"
echo "Out File:    $OUTFILE"
if [ ! "$INCLUDE_DIR" == "" ]; then
echo "Include Dir: $INCLUDE_DIR"
fi
echo "----------"

ROOT="$WORK_DIR/tupelo"

echo "Making work directories"
# Make our directories
mkdir -p "$ROOT"
mkdir "$ROOT/src"

# Mount the ISO
echo "Mounting the ISO"
sudo mount -o loop "$ISO" "$ROOT/src"

# Copy the ISO into a different folder
echo "Copying the ISO into a different folder to make changes"
cp -r "$ROOT/src" "$ROOT/iso"

echo "Unmount ISO"
sudo umount "$ROOT/src"

# Unsquash the live filesystem
echo "Unsquash the filesystem"
sudo unsquashfs -d "$ROOT/filesystem" "$ROOT/iso/casper/filesystem.squashfs"
# Remove the old filesystem.squashfs
sudo rm "$ROOT/iso/casper/filesystem.squashfs"

# Install the tupelo utilities
echo "Build and install the tupelo shell"
mvn package

pushd shell
# We need sudo because permissions get wonky
sudo bash install-shell.sh "$ROOT/filesystem/opt/dims" > /dev/null
popd

echo "Add the tupelo shell to the system's PATH"
sudo sed -i "s|PATH=\"|PATH=\"/opt/dims/bin:|" "$ROOT/filesystem/etc/environment"

echo "Making $USER part of the disks group"
sudo sed -i "/^disk:/ s/\$/$USER/" "$ROOT/filesystem/etc/group"

if [ ! "$INCLUDE_DIR" == "" ]; then
    echo "Adding files from $INCLUDE_DIR"
    sudo cp --preserve=mode -r "$INCLUDE_DIR"/* "$ROOT/filesystem/"
fi

# Repack
echo "Repacking the filesystem"
sudo mksquashfs "$ROOT/filesystem/" "$ROOT/iso/casper/filesystem.squashfs"

# Make the ISO file
echo "Create the ISO"
pushd "$ROOT/iso"
sudo mkisofs -o "$OUTFILE" -b isolinux/isolinux.bin -c isolinux/boot.cat -no-emul-boot \
-boot-load-size 4 -boot-info-table -J -R -V "Tupelo" .

popd

# All done, clean up
echo "Cleaning up"
sudo rm -rf "$ROOT"

echo "Image ready: $OUTFILE"
