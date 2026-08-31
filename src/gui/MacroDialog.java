package gui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Edits the diagram's named constants: rows of (name, value) exported as
 * "#define name value" ahead of the .dot digraph. A block parameter field
 * that names one of these is written as a bare reference to it instead of a
 * literal, so several blocks can share one knob.
 */
public class MacroDialog extends JDialog {

	private static final String NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

	private final Diagram diagram;
	private final DefaultTableModel model;
	private boolean changed = false;

	public static boolean edit(Window owner, Diagram diagram) {
		MacroDialog dlg = new MacroDialog(owner, diagram);
		dlg.setVisible(true);
		return dlg.changed;
	}

	private MacroDialog(Window owner, Diagram diagram) {
		super(owner, "Macros", ModalityType.APPLICATION_MODAL);
		this.diagram = diagram;

		model = new DefaultTableModel(new Object[] {"name", "value"}, 0);
		for (var e : diagram.macros.entrySet())
			model.addRow(new Object[] {e.getKey(), e.getValue()});
		JTable table = new JTable(model);
		table.putClientProperty("terminateEditOnFocusLost", true);

		JButton addBtn = new JButton("Add");
		addBtn.addActionListener(e -> model.addRow(new Object[] {"", ""}));
		JButton removeBtn = new JButton("Remove");
		removeBtn.addActionListener(e -> {
			int row = table.getSelectedRow();
			if (row >= 0)
				model.removeRow(row);
		});
		JPanel toolbar = new JPanel();
		toolbar.add(addBtn);
		toolbar.add(removeBtn);

		JButton ok = new JButton("OK");
		ok.addActionListener(e -> apply());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		JPanel buttons = new JPanel();
		buttons.add(ok);
		buttons.add(cancel);

		setLayout(new BorderLayout());
		JPanel content = new JPanel(new BorderLayout());
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
		content.add(toolbar, BorderLayout.NORTH);
		content.add(new JScrollPane(table), BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(ok);
		setSize(340, 320);
		setLocationRelativeTo(owner);
	}

	private void apply() {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (int i = 0; i < model.getRowCount(); i++) {
			String name = String.valueOf(model.getValueAt(i, 0)).trim();
			String value = String.valueOf(model.getValueAt(i, 1)).trim();
			if (name.isEmpty() && value.isEmpty())
				continue;
			if (!name.matches(NAME_PATTERN)) {
				complain("Macro name \"" + name + "\" must match " + NAME_PATTERN + ".");
				return;
			}
			if (value.isEmpty()) {
				complain("Macro \"" + name + "\" needs a value.");
				return;
			}
			if (result.containsKey(name)) {
				complain("Macro \"" + name + "\" is defined twice.");
				return;
			}
			result.put(name, value);
		}
		diagram.macros.clear();
		diagram.macros.putAll(result);
		diagram.dirty = true;
		changed = true;
		dispose();
	}

	private void complain(String message) {
		JOptionPane.showMessageDialog(this, message, "Invalid macro", JOptionPane.WARNING_MESSAGE);
	}
}
