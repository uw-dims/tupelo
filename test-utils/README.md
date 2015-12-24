Tupelo Test Utilities

stix-amqp-test:
This is a simple test program which reads a STIX document, pulls out the MD5 hashes,
sends an AMQP request to Tupelo to search for the hashes, prints any matches and writes
the matching file path/hash to a new STIX file.

The runner script needs all the jars from the build to be in $PWD/target - IE you must
run the script with the target directory on your current directory.

It takes two parameters: The input STIX file and the output file name. If the output
file exists, it will overwrite it without warning.

You can use the -v option to make it print the AQMP request/response

The AMQP URL can be specified with the -u option, or read from the 'tupelo.prp' file, if it exists.
Define the AMQP URL in the 'tupelo.prp' file with 'amqp.url : amqp://dims:dims@localhost/dims' - using
your URL instead of localhost


Building:
This needs the Tupelo artifacts need to be installed to the local Maven cache. Run 'mvn package install' in $GIT/tupelo
Once the Tupelo artifacts are installed, you can build it via 'mvn package'
