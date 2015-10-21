===================
Maven Tips
===================

****************************
Updating the Tupelo Version
****************************

Because bumpversion doesn't like to work with versions such as `1.2.1-SNAPSHOT`,
you can use Maven to set the version for you::

 mvn versions:set -DnewVersion=1.2.1-SNAPSHOT

To set snapshot version numbers, first use bumpversion, then Maven to properly set the version
in all the POMs.
