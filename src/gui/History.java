package gui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Snapshot-based undo/redo. Each state is the diagram's .dsg text, captured
 * after every mutation (MainFrame.touch()); undo/redo just re-parse a
 * snapshot into a fresh Diagram. Cheap at this scale and immune to missed
 * mutation paths.
 */
class History {

	private static final int LIMIT = 100;

	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private String current = new Diagram().snapshot();

	/** Start a new timeline from d (New/Open). */
	void reset(Diagram d) {
		undo.clear();
		redo.clear();
		current = d.snapshot();
	}

	/** Called after any mutation; a no-op when nothing actually changed. */
	void record(Diagram d) {
		String s = d.snapshot();
		if (s.equals(current))
			return;
		undo.push(current);
		while (undo.size() > LIMIT)
			undo.removeLast();
		redo.clear();
		current = s;
	}

	/** Steps back and returns the restored diagram, or null when at the start. */
	Diagram undo() {
		if (undo.isEmpty())
			return null;
		redo.push(current);
		current = undo.pop();
		return Diagram.fromSnapshot(current);
	}

	/** Steps forward and returns the restored diagram, or null when at the end. */
	Diagram redo() {
		if (redo.isEmpty())
			return null;
		undo.push(current);
		current = redo.pop();
		return Diagram.fromSnapshot(current);
	}
}
