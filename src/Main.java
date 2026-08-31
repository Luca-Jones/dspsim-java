import java.io.IOException;

import sim.SDFGraph;

public class Main {

	public static void main(String[] args) {
		if (args.length == 0) {
			gui.MainFrame.launch();
			return; // the Swing EDT keeps the JVM alive
		}
		System.exit(run(args));
	}

	// separated from main so tests can check exit codes without System.exit
	static int run(String[] args) {
		if (args.length > 2) {
			System.err.println("usage: Main [file.dot] [iterations]");
			return 2;
		}
		String file = args.length >= 1 ? args[0] : "z.dot";
		int iterations = 5;
		if (args.length == 2) {
			try {
				iterations = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("iterations must be an integer, was: " + args[1]);
				return 2;
			}
		}
		try {
			SDFGraph.loadFromFile(file).run(iterations);
		} catch (IOException e) {
			System.err.println("cannot read " + file + ": " + e.getMessage());
			return 1;
		}
		return 0;
	}
}
