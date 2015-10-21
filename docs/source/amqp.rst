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

