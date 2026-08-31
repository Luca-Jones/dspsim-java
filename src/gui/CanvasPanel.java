package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * The drawing surface. Interactions (dspflow-style):
 *  - palette stamp tool: click to place, Shift stamps several, Esc cancels
 *  - drag port dot to port dot (either direction) to wire
 *  - Shift/Ctrl+click adds or removes blocks from the selection
 *  - left-drag block moves selection, left-drag empty pans, middle-drag pans
 *  - right-drag rubber-band selects, right-click context menu
 *  - Ctrl+wheel zooms about the cursor, wheel scrolls, Shift+wheel horizontal
 *  - Del deletes, Ctrl+C/V copy-paste, double-click edits properties
 */
public class CanvasPanel extends JPanel {

	static final int GRID = 20;
	static final int STUB = 24;

	/** Semantic colors; dark mode just swaps the instance. */
	static final class Theme {
		Color bg, grid, blockFill, sourceFill, sinkFill, blockBorder, glyph, caption,
			wire, sel, portIn, portOut, portWarn, shadow, band;

		static Theme light() {
			Theme t = new Theme();
			t.bg = new Color(0xF6F7F9);
			t.grid = new Color(0xE2E5EA);
			t.blockFill = Color.WHITE;
			t.sourceFill = new Color(0xFDF6E3);
			t.sinkFill = new Color(0xE9F1FB);
			t.blockBorder = new Color(0x7A8290);
			t.glyph = new Color(0x2B3340);
			t.caption = new Color(0x6B7280);
			t.wire = new Color(0x4A7A6F);
			t.sel = new Color(0xE8861A);
			t.portIn = new Color(0x3B82F6);
			t.portOut = new Color(0x22C55E);
			t.portWarn = new Color(0xD05050);
			t.shadow = new Color(0, 0, 0, 18);
			t.band = new Color(0xE8861A);
			return t;
		}

		static Theme dark() {
			Theme t = new Theme();
			t.bg = new Color(0x1E2127);
			t.grid = new Color(0x2E323A);
			t.blockFill = new Color(0x262A31);
			t.sourceFill = new Color(0x3A3323);
			t.sinkFill = new Color(0x233240);
			t.blockBorder = new Color(0x555C66);
			t.glyph = new Color(0xE6E9EE);
			t.caption = new Color(0x9AA1AA);
			t.wire = new Color(0x5FAE9C);
			t.sel = new Color(0xF0A030);
			t.portIn = new Color(0x60A5FA);
			t.portOut = new Color(0x4ADE80);
			t.portWarn = new Color(0xE06060);
			t.shadow = new Color(0, 0, 0, 60);
			t.band = new Color(0xF0A030);
			return t;
		}
	}

	/** A port dot: a drawing anchor, not a model entity. */
	record Anchor(Block block, boolean output, int index, double x, double y) {}

	private enum Drag { NONE, PAN, MOVE, WIRE, BAND }

	private final MainFrame frame;
	private Diagram diagram;
	private Theme theme = Theme.light();
	private boolean dark = false;

	private double zoom = 1.0;
	private double panX = 40, panY = 40;

	private BlockType placing = null;
	private final Set<Block> selection = new LinkedHashSet<>();
	private Wire selectedWire = null;

	private Drag drag = Drag.NONE;
	private Point pressScreen;
	private Point2D pressModel;
	private final LinkedHashMap<Block, Point> moveOrigins = new LinkedHashMap<>();
	private boolean moved = false;
	/** Already-selected block Shift/Ctrl-clicked on press; deselected on
	 *  release unless the press turned into a group drag. */
	private Block pressToggle = null;
	private Anchor wireFrom = null;
	private Point2D wireTo = null;
	private Rectangle2D band = null;
	private Anchor hover = null;

	private record ClipBlock(BlockType type, int x, int y, LinkedHashMap<String, String> params) {}
	private record ClipWire(int src, int dst) {}
	private List<ClipBlock> clipBlocks = List.of();
	private List<ClipWire> clipWires = List.of();
	private int pasteCount = 0;

	public CanvasPanel(MainFrame frame, Diagram diagram) {
		this.frame = frame;
		this.diagram = diagram;
		setBackground(theme.bg);
		MouseAdapter mouse = new Mouse();
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
		bindKeys();
	}

	// ---- external control ----------------------------------------------

	/** Swap in a new diagram (New/Open) without recreating the canvas, so
	 *  view settings like dark mode survive. */
	public void setDiagram(Diagram d) {
		diagram = d;
		selection.clear();
		selectedWire = null;
		resetView();
	}

	public void setPlacing(BlockType type) {
		placing = type;
		setCursor(type == null ? Cursor.getDefaultCursor()
				: Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
	}

	public void setDarkMode(boolean on) {
		dark = on;
		theme = on ? Theme.dark() : Theme.light();
		setBackground(theme.bg);
		repaint();
	}

	public boolean isDarkMode() { return dark; }

	public void resetView() {
		zoom = 1.0;
		panX = 40;
		panY = 40;
		frame.setZoomLabel("100%");
		repaint();
	}

	// ---- geometry --------------------------------------------------------

	private static int snap(double v) {
		return (int) Math.round(v / GRID) * GRID;
	}

	private Point2D toModel(Point p) {
		return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
	}

	private List<Anchor> anchors(Block b) {
		List<Anchor> list = new ArrayList<>();
		if (!b.type.isSink())
			list.add(new Anchor(b, true, 0, b.x + Block.W, b.y + Block.H / 2.0));
		int n = inputCount(b);
		for (int i = 0; i < n; i++)
			list.add(new Anchor(b, false, i, b.x, b.y + Block.H * (i + 1) / (double) (n + 1)));
		return list;
	}

	/** Input dots: fixed for ONE/TWO; for sum, the configured "inputs" count,
	 *  growing further (wired plus one spare) as more wires arrive. */
	private int inputCount(Block b) {
		return switch (b.type.inArity) {
			case NONE -> 0;
			case ONE -> 1;
			case TWO -> 2;
			case MANY -> Math.max(Math.max(2, configuredInputs(b)),
					diagram.wiresInto(b).size() + 1);
		};
	}

	private static int configuredInputs(Block b) {
		try {
			return Integer.parseInt(b.params.getOrDefault("inputs", "").trim());
		} catch (NumberFormatException e) {
			return 2;
		}
	}

	private Anchor inputAnchor(Block b, int slot) {
		int n = inputCount(b);
		return new Anchor(b, false, slot, b.x, b.y + Block.H * (slot + 1) / (double) (n + 1));
	}

	private Anchor outputAnchor(Block b) {
		return new Anchor(b, true, 0, b.x + Block.W, b.y + Block.H / 2.0);
	}

	private Anchor anchorAt(Point2D m) {
		double r = Math.max(8, 8 / zoom);
		for (Block b : diagram.blocks)
			for (Anchor a : anchors(b))
				if (m.distance(a.x(), a.y()) <= r)
					return a;
		return null;
	}

	private Block blockAt(Point2D m) {
		for (int i = diagram.blocks.size() - 1; i >= 0; i--) {
			Block b = diagram.blocks.get(i);
			if (b.contains(m.getX(), m.getY()))
				return b;
		}
		return null;
	}

	private Wire wireAt(Point2D m) {
		double r = Math.max(5, 5 / zoom);
		for (Wire w : diagram.wires) {
			List<Point2D> pts = route(w);
			for (int i = 0; i + 1 < pts.size(); i++)
				if (Line2D.ptSegDist(pts.get(i).getX(), pts.get(i).getY(),
						pts.get(i + 1).getX(), pts.get(i + 1).getY(),
						m.getX(), m.getY()) <= r)
					return w;
		}
		return null;
	}

	/**
	 * Orthogonal auto-route. Forward wires jog once at a vertical grid line;
	 * feedback wires (destination left of source) detour below both blocks.
	 * The last segment is always horizontal into the input dot, so the
	 * arrowhead enters head-on.
	 */
	private List<Point2D> route(Wire w) {
		int slot = diagram.wiresInto(w.dst).indexOf(w);
		Anchor s = outputAnchor(w.src);
		Anchor t = inputAnchor(w.dst, Math.max(slot, 0));
		List<Point2D> pts = new ArrayList<>();
		pts.add(new Point2D.Double(s.x(), s.y()));
		double sx = s.x() + STUB, tx = t.x() - STUB;
		if (sx <= tx) {
			double mx = Math.max(sx, Math.min(tx, snap((sx + tx) / 2)));
			pts.add(new Point2D.Double(mx, s.y()));
			pts.add(new Point2D.Double(mx, t.y()));
		} else {
			double yc = snap(Math.max(w.src.y, w.dst.y) + Block.H + STUB);
			pts.add(new Point2D.Double(sx, s.y()));
			pts.add(new Point2D.Double(sx, yc));
			pts.add(new Point2D.Double(tx, yc));
			pts.add(new Point2D.Double(tx, t.y()));
		}
		pts.add(new Point2D.Double(t.x(), t.y()));
		return pts;
	}

	// ---- painting --------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(panX, panY);
		g2.scale(zoom, zoom);

		paintGrid(g2);
		for (Wire w : diagram.wires)
			paintWire(g2, w);
		if (drag == Drag.WIRE && wireFrom != null && wireTo != null)
			paintPendingWire(g2);
		for (Block b : diagram.blocks)
			paintBlock(g2, b);
		if (drag == Drag.BAND && band != null)
			paintBand(g2);
		g2.dispose();
	}

	private void paintGrid(Graphics2D g2) {
		double x0 = -panX / zoom, y0 = -panY / zoom;
		double x1 = x0 + getWidth() / zoom, y1 = y0 + getHeight() / zoom;
		g2.setColor(theme.grid);
		double d = 1.6;
		for (int x = snap(x0) - GRID; x <= x1 + GRID; x += GRID)
			for (int y = snap(y0) - GRID; y <= y1 + GRID; y += GRID)
				g2.fill(new Ellipse2D.Double(x - d / 2, y - d / 2, d, d));
	}

	private void paintWire(Graphics2D g2, Wire w) {
		List<Point2D> pts = route(w);
		g2.setColor(w == selectedWire ? theme.sel : theme.wire);
		g2.setStroke(new BasicStroke(w == selectedWire ? 2.4f : 1.6f,
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D path = new Path2D.Double();
		path.moveTo(pts.get(0).getX(), pts.get(0).getY());
		for (int i = 1; i < pts.size(); i++)
			path.lineTo(pts.get(i).getX(), pts.get(i).getY());
		g2.draw(path);
		// arrowhead: last segment is horizontal, entering the input dot
		Point2D end = pts.get(pts.size() - 1);
		double ax = end.getX(), ay = end.getY(), len = 9, half = 4;
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(ax, ay);
		arrow.lineTo(ax - len, ay - half);
		arrow.lineTo(ax - len, ay + half);
		arrow.closePath();
		g2.fill(arrow);
	}

	private void paintPendingWire(Graphics2D g2) {
		g2.setColor(theme.sel);
		g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
				1f, new float[] {6, 5}, 0));
		g2.draw(new Line2D.Double(wireFrom.x(), wireFrom.y(), wireTo.getX(), wireTo.getY()));
	}

	private void paintBlock(Graphics2D g2, Block b) {
		boolean selected = selection.contains(b);
		RoundRectangle2D rect = new RoundRectangle2D.Double(b.x, b.y, Block.W, Block.H, 10, 10);

		g2.setColor(theme.shadow);
		g2.fill(new RoundRectangle2D.Double(b.x + 3, b.y + 3, Block.W, Block.H, 10, 10));
		g2.setColor(switch (b.type.category) {
			case SOURCE -> theme.sourceFill;
			case SINK -> theme.sinkFill;
			default -> theme.blockFill;
		});
		g2.fill(rect);
		g2.setColor(selected ? theme.sel : theme.blockBorder);
		g2.setStroke(new BasicStroke(selected ? 2.2f : 1.3f));
		g2.draw(rect);

		g2.setColor(theme.glyph);
		String glyph = b.type.glyph(b);
		Font glyphFont = getFont().deriveFont(Font.BOLD, 20f);
		FontMetrics fm = g2.getFontMetrics(glyphFont);
		int maxWidth = Block.W - 12;
		if (fm.stringWidth(glyph) > maxWidth)
			glyphFont = glyphFont.deriveFont(
					Math.max(10f, 20f * maxWidth / fm.stringWidth(glyph)));
		g2.setFont(glyphFont);
		fm = g2.getFontMetrics();
		g2.drawString(glyph,
				b.x + (Block.W - fm.stringWidth(glyph)) / 2f,
				b.y + (Block.H + fm.getAscent() - fm.getDescent()) / 2f);

		g2.setColor(theme.caption);
		g2.setFont(getFont().deriveFont(10f));
		fm = g2.getFontMetrics();
		g2.drawString(b.name, b.x + (Block.W - fm.stringWidth(b.name)) / 2f, b.y + Block.H + 13);

		paintPorts(g2, b);
	}

	private void paintPorts(Graphics2D g2, Block b) {
		int wired = diagram.wiresInto(b).size();
		int needed = switch (b.type.inArity) {
			case NONE -> 0;
			case ONE -> 1;
			default -> 2;
		};
		g2.setStroke(new BasicStroke(1.6f));
		for (Anchor a : anchors(b)) {
			boolean hot = hover != null && hover.block() == a.block()
					&& hover.output() == a.output() && hover.index() == a.index();
			double r = hot ? 5 : 4;
			Ellipse2D dot = new Ellipse2D.Double(a.x() - r, a.y() - r, 2 * r, 2 * r);
			if (a.output()) {
				g2.setColor(hot ? theme.sel : theme.portOut);
				g2.fill(dot);
			} else if (a.index() < wired) {
				g2.setColor(hot ? theme.sel : theme.portIn);
				g2.fill(dot);
			} else {
				// empty input: hollow ring, red when the engine would reject the wiring
				g2.setColor(theme.bg);
				g2.fill(dot);
				g2.setColor(hot ? theme.sel : wired < needed ? theme.portWarn : theme.portIn);
				g2.draw(dot);
			}
		}
	}

	private void paintBand(Graphics2D g2) {
		g2.setColor(new Color(theme.band.getRed(), theme.band.getGreen(), theme.band.getBlue(), 24));
		g2.fill(band);
		g2.setColor(theme.band);
		g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
				1f, new float[] {5, 4}, 0));
		g2.draw(band);
	}

	// ---- mouse -----------------------------------------------------------

	private class Mouse extends MouseAdapter {

		@Override
		public void mousePressed(MouseEvent e) {
			requestFocusInWindow();
			pressScreen = e.getPoint();
			pressModel = toModel(e.getPoint());
			moved = false;
			pressToggle = null;

			if (SwingUtilities.isMiddleMouseButton(e)) {
				drag = Drag.PAN;
				return;
			}
			if (SwingUtilities.isRightMouseButton(e)) {
				Block b = blockAt(pressModel);
				if (b != null && !selection.contains(b)) {
					selection.clear();
					selection.add(b);
					selectedWire = null;
				}
				drag = b == null ? Drag.BAND : Drag.NONE;
				band = null;
				repaint();
				return;
			}
			if (!SwingUtilities.isLeftMouseButton(e))
				return;

			if (placing != null) {
				Block b = diagram.addBlock(placing,
						snap(pressModel.getX() - Block.W / 2.0),
						snap(pressModel.getY() - Block.H / 2.0));
				selection.clear();
				selection.add(b);
				selectedWire = null;
				frame.setStatus("Placed " + b.name + "."
						+ (e.isShiftDown() ? " Click to place another." : ""));
				if (!e.isShiftDown())
					frame.clearPlacing();
				frame.touch();
				repaint();
				return;
			}

			Anchor a = anchorAt(pressModel);
			if (a != null) {
				drag = Drag.WIRE;
				wireFrom = a;
				wireTo = pressModel;
				return;
			}
			Block b = blockAt(pressModel);
			if (b != null) {
				selectedWire = null;
				if (e.isShiftDown() || e.isControlDown()) {
					if (!selection.add(b))
						pressToggle = b; // toggle off on release, unless dragged
				} else if (!selection.contains(b)) {
					selection.clear();
					selection.add(b);
				}
				drag = Drag.MOVE;
				moveOrigins.clear();
				for (Block s : selection)
					moveOrigins.put(s, new Point(s.x, s.y));
				repaint();
				return;
			}
			Wire w = wireAt(pressModel);
			if (w != null) {
				selection.clear();
				selectedWire = w;
				frame.setStatus(w.src.name + " → " + w.dst.name + "  (Del deletes the wire)");
				repaint();
				return;
			}
			if (!e.isControlDown() && !e.isShiftDown()) {
				selection.clear();
				selectedWire = null;
			}
			drag = Drag.PAN;
			repaint();
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			Point2D m = toModel(e.getPoint());
			if (e.getPoint().distance(pressScreen) > 3)
				moved = true;
			switch (drag) {
				case PAN -> {
					panX += e.getX() - pressScreen.x;
					panY += e.getY() - pressScreen.y;
					pressScreen = e.getPoint();
					repaint();
				}
				case MOVE -> {
					double dx = m.getX() - pressModel.getX();
					double dy = m.getY() - pressModel.getY();
					for (var en : moveOrigins.entrySet()) {
						en.getKey().x = snap(en.getValue().x + dx);
						en.getKey().y = snap(en.getValue().y + dy);
					}
					repaint();
				}
				case WIRE -> {
					wireTo = m;
					hover = anchorAt(m);
					repaint();
				}
				case BAND -> {
					band = new Rectangle2D.Double(
							Math.min(pressModel.getX(), m.getX()),
							Math.min(pressModel.getY(), m.getY()),
							Math.abs(m.getX() - pressModel.getX()),
							Math.abs(m.getY() - pressModel.getY()));
					repaint();
				}
				default -> {}
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			Point2D m = toModel(e.getPoint());
			switch (drag) {
				case WIRE -> finishWire(m);
				case MOVE -> {
					if (moved) {
						diagram.dirty = true;
						frame.touch();
					} else if (pressToggle != null) {
						selection.remove(pressToggle);
					}
					pressToggle = null;
				}
				case BAND -> {
					if (band != null) {
						if (!e.isControlDown() && !e.isShiftDown())
							selection.clear();
						for (Block b : diagram.blocks)
							if (band.intersects(b.x, b.y, Block.W, Block.H))
								selection.add(b);
						selectedWire = null;
					}
					band = null;
				}
				default -> {}
			}
			if (SwingUtilities.isRightMouseButton(e) && !moved)
				showPopup(e, m);
			drag = Drag.NONE;
			wireFrom = null;
			wireTo = null;
			repaint();
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			Anchor a = anchorAt(toModel(e.getPoint()));
			if (a != hover) {
				hover = a;
				repaint();
			}
			if (placing == null)
				setCursor(a != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
						: Cursor.getDefaultCursor());
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
				Block b = blockAt(toModel(e.getPoint()));
				if (b != null)
					editProperties(b);
			}
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if (e.isControlDown()) {
				double factor = Math.pow(1.12, -e.getWheelRotation());
				double newZoom = Math.max(0.2, Math.min(4.0, zoom * factor));
				// keep the model point under the cursor fixed
				Point2D m = toModel(e.getPoint());
				panX = e.getX() - m.getX() * newZoom;
				panY = e.getY() - m.getY() * newZoom;
				zoom = newZoom;
				frame.setZoomLabel(Math.round(zoom * 100) + "%");
			} else if (e.isShiftDown()) {
				panX -= e.getWheelRotation() * 40;
			} else {
				panY -= e.getWheelRotation() * 40;
			}
			repaint();
		}
	}

	private void finishWire(Point2D m) {
		Anchor target = anchorAt(m);
		Block targetBlock = target != null ? target.block() : blockAt(m);
		if (targetBlock == null || targetBlock == wireFrom.block()) {
			frame.setStatus("Wiring cancelled.");
			return;
		}
		Block src = wireFrom.output() ? wireFrom.block() : targetBlock;
		Block dst = wireFrom.output() ? targetBlock : wireFrom.block();
		String err = diagram.connect(src, dst);
		if (err != null) {
			frame.setStatus(err);
		} else {
			frame.setStatus("Wired " + src.name + " → " + dst.name + ".");
			frame.touch();
		}
	}

	private void editProperties(Block b) {
		if (PropertyDialog.edit(SwingUtilities.getWindowAncestor(this), diagram, b)) {
			frame.setStatus("Updated " + b.name + ".");
			frame.touch();
			repaint();
		}
	}

	private void showPopup(MouseEvent e, Point2D m) {
		JPopupMenu menu = new JPopupMenu();
		Block b = blockAt(m);
		Wire w = b == null ? wireAt(m) : null;
		if (b != null) {
			JMenuItem props = new JMenuItem("Properties…");
			props.addActionListener(ev -> editProperties(b));
			menu.add(props);
			JMenuItem del = new JMenuItem("Delete");
			del.addActionListener(ev -> deleteSelection());
			menu.add(del);
		} else if (w != null) {
			selectedWire = w;
			selection.clear();
			JMenuItem del = new JMenuItem("Delete wire");
			del.addActionListener(ev -> deleteSelection());
			menu.add(del);
		} else {
			JMenuItem reset = new JMenuItem("Reset view");
			reset.addActionListener(ev -> resetView());
			menu.add(reset);
		}
		menu.show(this, e.getX(), e.getY());
	}

	// ---- keyboard --------------------------------------------------------

	private void bindKeys() {
		InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = getActionMap();
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
		am.put("delete", action(this::deleteSelection));
		am.put("escape", action(this::escape));
	}

	private AbstractAction action(Runnable r) {
		return new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent e) { r.run(); }
		};
	}

	private void deleteSelection() {
		if (selection.isEmpty() && selectedWire == null)
			return;
		int n = selection.size();
		for (Block b : List.copyOf(selection))
			diagram.remove(b);
		selection.clear();
		if (selectedWire != null) {
			diagram.remove(selectedWire);
			selectedWire = null;
			frame.setStatus("Deleted wire.");
		} else {
			frame.setStatus("Deleted " + n + " block" + (n == 1 ? "" : "s") + ".");
		}
		frame.touch();
		repaint();
	}

	private void escape() {
		frame.clearPlacing();
		selection.clear();
		selectedWire = null;
		drag = Drag.NONE;
		band = null;
		frame.setStatus("Ready.");
		repaint();
	}

	/** Copies the selected blocks (and the wires between them) to an internal
	 *  clipboard. Ctrl+C via the Edit menu accelerator. */
	public void copySelection() {
		if (selection.isEmpty()) {
			frame.setStatus("Nothing selected — Shift+click or right-drag to select blocks.");
			return;
		}
		List<Block> ordered = new ArrayList<>(selection);
		List<ClipBlock> cb = new ArrayList<>();
		for (Block b : ordered)
			cb.add(new ClipBlock(b.type, b.x, b.y, new LinkedHashMap<>(b.params)));
		List<ClipWire> cw = new ArrayList<>();
		for (Wire w : diagram.wires) {
			int si = ordered.indexOf(w.src), di = ordered.indexOf(w.dst);
			if (si >= 0 && di >= 0)
				cw.add(new ClipWire(si, di));
		}
		clipBlocks = cb;
		clipWires = cw;
		pasteCount = 0;
		frame.setStatus("Copied " + cb.size() + " block" + (cb.size() == 1 ? "" : "s") + ".");
	}

	/** Pastes the clipboard one grid step down-right of the copied blocks,
	 *  leaving the new blocks selected. Ctrl+V via the Edit menu accelerator. */
	public void paste() {
		if (clipBlocks.isEmpty()) {
			frame.setStatus("Clipboard is empty — copy some blocks first.");
			return;
		}
		int off = GRID * ++pasteCount;
		List<Block> pasted = new ArrayList<>();
		for (ClipBlock c : clipBlocks) {
			Block b = diagram.addBlock(c.type(), c.x() + off, c.y() + off);
			b.params.putAll(c.params());
			pasted.add(b);
		}
		for (ClipWire c : clipWires)
			diagram.connect(pasted.get(c.src()), pasted.get(c.dst()));
		selection.clear();
		selection.addAll(pasted);
		selectedWire = null;
		frame.setStatus("Pasted " + pasted.size() + " block" + (pasted.size() == 1 ? "" : "s") + ".");
		frame.touch();
		repaint();
	}
}
