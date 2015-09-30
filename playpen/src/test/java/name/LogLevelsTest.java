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
package name;

import org.apache.log4j.*;
import org.apache.commons.logging.*;

/**
 * Result: If we want many classes in a package to share a logger in a
 * log4j logging system, and thus ALL see the results of a single
 * setLevel() call, all loggers MUST be declared with the SAME
 * string. So as long as all the classes are in the same package,we
 * use the common package name, so THIS works:
 *
 * log = Logger.getLogger( getClass().getPackage().getName() );
 *
 * but this does NOT work (since individual loggers are created for
 * each class and thus have different names, so setting one's level
 * does NOT affect any others, this is how log4j works):
 *
 * log = Logger.getLogger( getClass() );
 *
 * We fabricate four classes A,B,C,D all in the same package.  D uses
 * commons logging, just to see if its associated log4j logger impl
 * follows same rules for level sharing as explicitly declared log4j
 * loggers.  We cannot test/inspect D' log level directly, so have to
 * just visually inspect the output of its log messages.
 *
 */
public class LogLevelsTest extends junit.framework.TestCase {

	static class A {
		A() {

			/*
			  This is what NOT to do if you want all classes to use SAME
			  logger and so all 'see' the effects of a setLevel() call
			*/
			//			log = Logger.getLogger( getClass() );


			/*
			  This IS what to do, since all log creations then use the SAME
			  string
			*/
			log = Logger.getLogger( getClass().getPackage().getName() );

			log.debug( log.getName() );
		}
		protected Logger log;
	}

	static class B extends A {
	}

	static class C extends A {
	}

	static class D {
		D() {
			log = LogFactory.getLog( getClass().getPackage().getName() );
			log.debug( "D" );
		}
		Log log;
	}
	
	public void testNull() {
	}

	/**
	 * The test is: create some objects of distincts classes, each
	 * with a logger which is supposedly using package name for logger
	 * name.  We then set the level in one logger and hope it observe
	 * it changed in other loggers.
	 */
	public void testSharedLevels() {
		B b = new B();
		C c = new C();
		D d = new D();
		
		System.out.println( b.log.getLevel() );
		System.out.println( c.log.getLevel() );

		System.out.println( b.log.getEffectiveLevel() );
		System.out.println( c.log.getEffectiveLevel() );

		b.log.setLevel( Level.WARN );

		System.out.println( b.log.getLevel() );
		System.out.println( c.log.getLevel() );

		assertEquals( b.log.getLevel(), c.log.getLevel() );
		
		System.out.println( b.log.getEffectiveLevel() );
		System.out.println( c.log.getEffectiveLevel() );

		d.log.debug( "D.debug. You should NOT see me!" );
		d.log.info( "D.info, You should NOT see me!" );
		d.log.warn( "D.warn.  You should see me." );
	}
}

// eof
