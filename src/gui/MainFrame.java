package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import sim.SDFGraph;

/**
 * The editor window. The GUI's whole job is to produce a .dot file; the one
 * and only engine touchpoint is runSim(), which compiles the drawing to that
 * file and calls SDFGraph.loadFromFile(path).run(iterations).
 */
public class MainFrame extends JFrame {

	private Diagram diagram = new Diagram();
	private final History history = new History();
	private final CanvasPanel canvas;
	private final JLabel status = new JLabel();
	private final JLabel zoomLabel = new JLabel("100%");
	private JSpinner iterations;
	private JButton runButton;
	private JToggleButton selectButton;
	private File currentFile;

	public static void launch() {
		SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
	}

	public MainFrame() {
		super("dspsim");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) {
				if (confirmDiscard())
					dispose();
			}
		});
		setSize(1180, 760);
		setLocationByPlatform(true);

		canvas = new CanvasPanel(this, diagram);

		setJMenuBar(buildMenuBar());
		add(buildToolbar(), BorderLayout.NORTH);
		add(buildPalette(), BorderLayout.WEST);
		add(canvas, BorderLayout.CENTER);

		status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		setStatus("Pick a block, click the canvas to place (Shift stamps several). "
				+ "Drag port to port to wire. Left-drag pans, right-drag selects, "
				+ "Shift+click multi-selects, Ctrl+C/V copies, Ctrl+Z/Y undoes/redoes, "
				+ "R/Shift+R rotates, H/V flips, Ctrl+wheel zooms, Del deletes, Esc cancels.");
		add(status, BorderLayout.SOUTH);
	}

	// ---- UI construction -------------------------------------------------

	private JMenuBar buildMenuBar() {
		JMenuBar mb = new JMenuBar();

		JMenu file = new JMenu("File");
		file.add(item("New", KeyEvent.VK_N, e -> newDiagram()));
		file.add(item("Open…", KeyEvent.VK_O, e -> open()));
		file.add(item("Save", KeyEvent.VK_S, e -> save(false)));
		file.add(item("Save As…", 0, e -> save(true)));
		file.addSeparator();
		file.add(item("Export .dot…", 0, e -> exportDot()));
		file.addSeparator();
		file.add(item("Exit", 0, e -> {
			if (confirmDiscard())
				dispose();
		}));
		mb.add(file);

		JMenu edit = new JMenu("Edit");
		edit.add(item("Undo", KeyEvent.VK_Z, e -> undoRedo(true)));
		edit.add(item("Redo", KeyEvent.VK_Y, e -> undoRedo(false)));
		edit.addSeparator();
		edit.add(item("Copy", KeyEvent.VK_C, e -> canvas.copySelection()));
		edit.add(item("Paste", KeyEvent.VK_V, e -> canvas.paste()));
		edit.addSeparator();
		edit.add(item("Macros…", 0, e -> editMacros()));
		mb.add(edit);

		JMenu sim = new JMenu("Simulate");
		sim.add(item("Compile & Run", KeyEvent.VK_R, e -> runSim()));
		mb.add(sim);

		JMenu view = new JMenu("View");
		view.add(item("Reset view", 0, e -> canvas.resetView()));
		view.addSeparator();
		JCheckBoxMenuItem darkMode = new JCheckBoxMenuItem("Dark mode", false);
		darkMode.addActionListener(e -> canvas.setDarkMode(darkMode.isSelected()));
		view.add(darkMode);
		mb.add(view);

		JMenu help = new JMenu("Help");
		help.add(item("About…", 0, e -> JOptionPane.showMessageDialog(this,
				"dspsim gui — draws a block diagram, compiles it to a Graphviz .dot\n"
				+ "file, and runs the dspsim engine on it. Plots via python/plot.py.",
				"About", JOptionPane.INFORMATION_MESSAGE)));
		mb.add(help);
		return mb;
	}

	private JMenuItem item(String name, int key, java.awt.event.ActionListener a) {
		JMenuItem it = new JMenuItem(name);
		if (key != 0)
			it.setAccelerator(KeyStroke.getKeyStroke(key,
					Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
		it.addActionListener(a);
		return it;
	}

	private JToolBar buildToolbar() {
		JToolBar tb = new JToolBar();
		tb.setFloatable(false);
		tb.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		tb.add(new JLabel("Iterations: "));
		iterations = new JSpinner(new SpinnerNumberModel(8, 1, 1_000_000, 1));
		iterations.setMaximumSize(new Dimension(90, 28));
		tb.add(iterations);
		tb.addSeparator();

		runButton = new JButton("▶ Compile & Run");
		runButton.setToolTipText("Compile the drawing to a .dot file, run the simulation, plot the results (Ctrl+R)");
		runButton.addActionListener(e -> runSim());
		tb.add(runButton);

		tb.add(Box.createHorizontalGlue());
		tb.add(zoomLabel);
		return tb;
	}

	private JComponent buildPalette() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
		ButtonGroup group = new ButtonGroup();

		selectButton = paletteButton(group, "↖ Select", null);
		selectButton.setSelected(true);
		p.add(selectButton);
		p.add(Box.createVerticalStrut(8));

		for (BlockType.Category cat : BlockType.Category.values()) {
			JLabel header = new JLabel(switch (cat) {
				case SOURCE -> "Sources";
				case MATH -> "Math";
				case RATE -> "Rate";
				case SINK -> "Sink";
			});
			header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
			header.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(header);
			for (BlockType t : BlockType.values())
				if (t.category == cat)
					p.add(paletteButton(group, t.dotType, t));
			p.add(Box.createVerticalStrut(8));
		}
		p.add(Box.createVerticalGlue());

		JScrollPane sp = new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xD7DBE0)));
		return sp;
	}

	private JToggleButton paletteButton(ButtonGroup group, String label, BlockType type) {
		JToggleButton b = new JToggleButton(label);
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(110, 28));
		b.setFocusPainted(false);
		if (type != null)
			b.setToolTipText(type.description);
		group.add(b);
		b.addActionListener(e -> {
			canvas.setPlacing(type);
			if (type != null)
				setStatus("Click the canvas to place a " + type.dotType
						+ " (hold Shift to place several, Esc to cancel).");
		});
		return b;
	}

	// ---- canvas callbacks --------------------------------------------------

	public void setStatus(String s) { status.setText(" " + s); }

	public void setZoomLabel(String s) { zoomLabel.setText(s); }

	public void clearPlacing() {
		selectButton.setSelected(true);
		canvas.setPlacing(null);
	}

	/** Called by the canvas after any model mutation; snapshots for undo and
	 *  refreshes the title. */
	public void touch() {
		history.record(diagram);
		refreshTitle();
	}

	private void undoRedo(boolean undo) {
		Diagram d = undo ? history.undo() : history.redo();
		if (d == null) {
			setStatus(undo ? "Nothing to undo." : "Nothing to redo.");
			return;
		}
		diagram = d;
		diagram.dirty = true;
		canvas.setDiagram(diagram, false);
		refreshTitle();
		setStatus(undo ? "Undo." : "Redo.");
	}

	private void refreshTitle() {
		setTitle("dspsim — " + (currentFile == null ? "untitled" : currentFile.getName())
				+ (diagram.dirty ? " *" : ""));
	}

	private void editMacros() {
		if (MacroDialog.edit(this, diagram)) {
			touch();
			setStatus("Updated macros.");
		}
	}

	// ---- file actions ------------------------------------------------------

	private boolean confirmDiscard() {
		if (!diagram.dirty)
			return true;
		return JOptionPane.showConfirmDialog(this, "Discard unsaved changes?",
				"Unsaved changes", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
	}

	private void newDiagram() {
		if (!confirmDiscard())
			return;
		diagram = new Diagram();
		currentFile = null;
		history.reset(diagram);
		canvas.setDiagram(diagram);
		refreshTitle();
		setStatus("New diagram.");
	}

	private void open() {
		if (!confirmDiscard())
			return;
		JFileChooser fc = chooser("dspsim diagrams (*.dsg)", "dsg");
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		File f = fc.getSelectedFile();
		try {
			diagram = Diagram.load(f);
			currentFile = f;
			history.reset(diagram);
			canvas.setDiagram(diagram);
			refreshTitle();
			setStatus("Loaded " + f.getName() + ".");
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Could not load " + f.getName() + ":\n" + ex,
					"Open failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Returns true when the diagram ended up saved. */
	private boolean save(boolean as) {
		File f = currentFile;
		if (as || f == null) {
			JFileChooser fc = chooser("dspsim diagrams (*.dsg)", "dsg");
			if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				return false;
			f = fc.getSelectedFile();
			if (!f.getName().contains("."))
				f = new File(f.getPath() + ".dsg");
		}
		try {
			diagram.save(f);
			currentFile = f;
			refreshTitle();
			setStatus("Saved " + f.getName() + ".");
			return true;
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Could not save:\n" + ex,
					"Save failed", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private JFileChooser chooser(String description, String ext) {
		JFileChooser fc = new JFileChooser(
				currentFile != null ? currentFile.getParentFile() : new File("."));
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description, ext));
		return fc;
	}

	// ---- compile & run -----------------------------------------------------

	private boolean reportProblems() {
		List<String> problems = DotWriter.validate(diagram);
		if (problems.isEmpty())
			return false;
		JOptionPane.showMessageDialog(this,
				String.join("\n", problems), "Diagram problems", JOptionPane.WARNING_MESSAGE);
		return true;
	}

	private void exportDot() {
		if (reportProblems())
			return;
		JFileChooser fc = chooser("Graphviz graphs (*.dot)", "dot");
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		File f = fc.getSelectedFile();
		if (!f.getName().contains("."))
			f = new File(f.getPath() + ".dot");
		try {
			DotWriter.write(diagram, f.toPath());
			setStatus("Exported " + f.getName() + ".");
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Could not write .dot:\n" + ex,
					"Export failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void runSim() {
		if (reportProblems())
			return;
		if (currentFile == null) {
			setStatus("Save the diagram first — the .dot file is written next to it.");
			if (!save(true))
				return;
		} else if (!save(false)) {
			return;
		}
		File dotFile = new File(currentFile.getParentFile(),
				currentFile.getName().replaceAll("\\.dsg$", "") + ".dot");
		try {
			DotWriter.write(diagram, dotFile.toPath());
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Could not write " + dotFile + ":\n" + ex,
					"Compile failed", JOptionPane.ERROR_MESSAGE);
			return;
		}
		int n = (Integer) iterations.getValue();
		runButton.setEnabled(false);
		setStatus("Compiled " + dotFile.getName() + " — running " + n + " iterations…");
		new Thread(() -> {
			try {
				long t0 = System.nanoTime();
				SDFGraph.loadFromFile(dotFile.getPath()).run(n);
				long ms = (System.nanoTime() - t0) / 1_000_000;
				SwingUtilities.invokeLater(() -> {
					setStatus("Ran " + n + " iterations in " + ms + " ms.");
					launchPlots();
				});
			} catch (Exception ex) {
				SwingUtilities.invokeLater(() -> {
					setStatus("Simulation failed.");
					String msg = ex.getMessage() != null ? ex.getMessage()
							: ex.getClass().getSimpleName();
					JOptionPane.showMessageDialog(this, msg,
							"Simulation error", JOptionPane.ERROR_MESSAGE);
				});
			} finally {
				SwingUtilities.invokeLater(() -> runButton.setEnabled(true));
			}
		}, "dspsim-run").start();
	}

	/** Fire-and-forget matplotlib windows for every dataout CSV that exists.
	 *  The engine writes files relative to the JVM working directory. */
	private void launchPlots() {
		Set<String> csvs = new LinkedHashSet<>();
		int consoleSinks = 0;
		for (Block b : diagram.blocks) {
			if (b.type != BlockType.DATAOUT)
				continue;
			String file = b.params.getOrDefault("file", "").trim();
			if (file.isEmpty())
				consoleSinks++;
			else if (Files.exists(Path.of(file)))
				csvs.add(file);
		}
		String note = consoleSinks > 0 ? " (" + consoleSinks + " sink(s) printed to the console)" : "";
		if (csvs.isEmpty()) {
			if (consoleSinks > 0)
				setStatus("Done — output went to the console.");
			return;
		}
		Path plotPy = Path.of("python", "plot.py");
		if (!Files.exists(plotPy)) {
			setStatus("Done, wrote " + String.join(", ", csvs)
					+ " — python/plot.py not found (run from the repo root to auto-plot)." + note);
			return;
		}
		Path venv = Path.of(".venv", "bin", "python3");
		String python = Files.isExecutable(venv) ? venv.toString() : "python3";
		List<String> cmd = new ArrayList<>();
		cmd.add(python);
		cmd.add(plotPy.toString());
		cmd.addAll(csvs);
		try {
			new ProcessBuilder(cmd)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			setStatus("Done — plotting " + String.join(", ", csvs) + "." + note);
		} catch (IOException ex) {
			setStatus("Done, wrote " + String.join(", ", csvs) + " — " + python
					+ " not found, plot manually: python3 python/plot.py " + String.join(" ", csvs));
		}
	}
}
