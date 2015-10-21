=================================
AMQP RPC
=================================

***************
Introduction
***************

All the RPC structures used by Tupelo follow the same standard RPC
structures defined in the `Wiki <http://foswiki.prisem.washington.edu/Development/AMQP_RPC#AMQP_for_Remote_Procedure_Calling>`_

A quick request example::

 {
    "appdata": {
        "key": "<Request Key/Values go in the appdata object>"
    },
    "hostname": "dddesktop",
    "name": "cifbulk",
    "pid": 4384,
    "pika_version": "0.9.8",
    "platform": "#25~precise1-Ubuntu SMP Thu Jan 30 17:39:31 UTC 2014",
    "protocolver": "0.5",
    "release": "0.5.5",
    "time": 1413590832
 }

And a response expample::

 {
    "appdata": {
        "retcode": 0,
        "stderr": "",
        "stdout": "<Base64 Encoded Output>"
    },
    "hostname": "dddesktop",
    "name": "cifbulk",
    "pid": 4269,
    "pika_version": "0.9.8",
    "platform": "#25~precise1-Ubuntu SMP Thu Jan 30 17:39:31 UTC 2014",
    "protocolver": "0.5",
    "release": "0.5.5",
    "time": 1413590836
 }

******************
File Hash Request
******************

To send a request to get details about any files that match a specific MD5 hash,
send a request on the `tupelo` exchange with a binding key of `who-has`.
Currently only MD5 hashes are supported, and you can send as many hashes as you want to look for.

The message's appdata structure is as follows::

 {
    "algorithm" : "md5",
    "hashes" : ["hash1hex", "hash2hex" "ect..." ]
 }

The response's appdata will contain the following::

 {
    "algorithm" : "md5",
    "hits": [
        {
            "hash": "hashHex",
            "descriptor": "(Disk Descriptor)",
            "path": "/path/to/file1"
        },
        {
            "hash": "hashHex",
            "descriptor": "(Disk Descriptor)",
            "path": "/path/to/file2"
        }
    ]
 }

Note that searching for hashes only works on images which have file records associated with the image.
The Tupelo server periodically checks for and generates these records, but it is a time consuming process,
and the records are only saved after analyzing all the files.
