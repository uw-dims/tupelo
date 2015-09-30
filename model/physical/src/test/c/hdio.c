/**
 * Copyright © 2015, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 *     * Neither the name of the University of Washington nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL UNIVERSITY OF
 * WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <fcntl.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <linux/hdreg.h>

/**
   discover device (disk) parameters via the HDIO_GET_IDENTITY ioctl,
   as used by e.g hdparm.

   By inspection of result buf, looks like

   buf[20-33] = serial num (14), may be whitespace-padded internally.
   buf[54-68] = manufacturer, model (what scsi INQUIRY calls 'VendorID'??)

   Note: fails on a usb external drive. scsi INQUIRY worked on this device,
   so might be a better choice since works with more drive types??
*/

int main( int argc, char* argv[] ) {

    char *device = "/dev/sda";
	if( argc > 1 )
	  device = argv[1];

	
	int fd = open( device, O_RDONLY);
  if( fd == -1 ) {
	perror("open");
	return -1;
  }

  char identity[512];
  int sc = ioctl( fd, HDIO_GET_IDENTITY, identity );
  if( sc == -1 ) {
	perror( "ioctl" );
	close( fd );
	return -1;
  }
  int i;
  for( i = 0; i < sizeof( identity ); i++ ) {
	if( identity[i] ) {
	  printf( "%d %c (%x)\n", i , identity[i], identity[i] & 0xff );
	}
  }
	
}

// eof

