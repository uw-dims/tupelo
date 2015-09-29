package edu.uw.apl.tupelo.shell;

/**
  For the purposes of creating a runnable jar with Elvis
  invoked by jline.ConsoleRunner, providing very nice command line
  editing features.
*/
public class JLine {

	static public void main( String[] args ) throws Exception {
		String[] jlineArgs = new String[args.length+1];
		// Run the main class
		jlineArgs[0] = Elvis.class.getName();
		// Copy all the command line args over
		for( int i = 0; i < args.length; i++ ){
			jlineArgs[i+1] = args[i];
		}
		// Run it
		jline.ConsoleRunner.main( jlineArgs );
	}
}
