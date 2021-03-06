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
package edu.uw.apl.tupelo.model;

import java.io.InputStream;
import java.io.IOException;
import java.util.Random;

/**
 * A test/fake implementation of an UnmanagedDisk.  This one is an
 * in-memory disk (needs no real backing disk) which produces random
 * data when read.  The 'random' nature does NOT give a new random
 * buffer for each read, it just re-uses the same buffer.  The only
 * way the local buffer is filled with fresh 'random' data is when a
 * larger buffer is needed, due to a larger length being read than we
 * had previously seen.
 */
public class RandomDisk extends MemoryDisk {

	/**
	 * @param size byte count for this disk
	 */
	public RandomDisk( long size ) {
		super( size );
	}
	
	public RandomDisk( long size, String id ) {
		super( size, id );
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return new RandomDiskInputStream();
	}

	class RandomDiskInputStream extends MemoryDiskInputStream {
		RandomDiskInputStream() {
			buffer = new byte[0];
			rng = new Random();
		}
		
		/**
		 * @param actual - NOT the len that the caller passed, but
		 * a computed maximum byte count from
		 * {@link MemoryDisk#read(byte[],int,int)}
		 */
		@Override
		protected int readImpl( byte[] b, int off, int actual )
			throws IOException {
			
			if( actual > buffer.length ) {
				// LOOK: could blow up, what if actual=MaxInt ??
				buffer = new byte[actual];
				for( int i = 0; i < buffer.length; i++ )
					buffer[i] = (byte)rng.nextInt();
			}
			System.arraycopy( buffer, 0, b, off, actual );
			return actual;
		}
		
		private byte[] buffer;
		private final Random rng;
	}
}

// eof
