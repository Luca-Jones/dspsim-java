package gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Generic modal editor: a name row plus one text field per parameter in the
 * block type's spec. The name must be a unique .dot identifier; int params
 * must parse (or be blank if optional). Required params may be left blank
 * here — compile-time validation reports them with full context. A SUB
 * block also gets one field per sub-diagram parameter; blank keeps the
 * sub-diagram's own value.
 */
public class PropertyDialog extends JDialog {

	private static final String NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

	private final Diagram diagram;
	private final Block block;
	private final JTextField nameField;
	private final List<BlockType.Param> params;
	private final JTextField[] paramFields;
	private boolean changed = false;

	public static boolean edit(Window owner, Diagram diagram, Block block, List<BlockType.Param> extra) {
		PropertyDialog dlg = new PropertyDialog(owner, diagram, block, extra);
		dlg.setVisible(true);
		return dlg.changed;
	}

	private PropertyDialog(Window owner, Diagram diagram, Block block, List<BlockType.Param> extra) {
		super(owner, block.type.dotType + " properties", ModalityType.APPLICATION_MODAL);
		this.diagram = diagram;
		this.block = block;
		this.params = new ArrayList<>(block.type.params);
		params.addAll(extra);

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(4, 4, 4, 4);
		gc.anchor = GridBagConstraints.WEST;
		gc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;
		nameField = addRow(grid, gc, row++, "name", block.name);
		paramFields = new JTextField[params.size()];
		for (int i = 0; i < paramFields.length; i++) {
			BlockType.Param p = params.get(i);
			boolean subParam = i >= block.type.params.size();
			String label = p.key() + (p.required() ? " *" : subParam ? " (" + p.defaultValue() + ")" : "");
			paramFields[i] = addRow(grid, gc, row++, label, block.params.getOrDefault(p.key(), ""));
			if (!diagram.macros.isEmpty())
				paramFields[i].setToolTipText("Macros: " + String.join(", ", diagram.macros.keySet()));
		}

		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		ok.addActionListener(e -> apply());
		cancel.addActionListener(e -> dispose());
		JPanel buttons = new JPanel();
		buttons.add(ok);
		buttons.add(cancel);

		add(grid, "Center");
		add(buttons, "South");
		getRootPane().setDefaultButton(ok);
		pack();
		setLocationRelativeTo(owner);
	}

	private JTextField addRow(JPanel grid, GridBagConstraints gc, int row, String label, String value) {
		gc.gridy = row;
		gc.gridx = 0;
		gc.weightx = 0;
		grid.add(new JLabel(label + ":"), gc);
		gc.gridx = 1;
		gc.weightx = 1;
		JTextField field = new JTextField(value, 14);
		grid.add(field, gc);
		return field;
	}

	private void apply() {
		String name = nameField.getText().trim();
		if (!name.matches(NAME_PATTERN)) {
			complain("Name must match " + NAME_PATTERN + " (it becomes the .dot node name).", nameField);
			return;
		}
		Block other = diagram.findByName(name);
		if (other != null && other != block) {
			complain("Another block is already named \"" + name + "\".", nameField);
			return;
		}
		for (int i = 0; i < paramFields.length; i++) {
			BlockType.Param p = params.get(i);
			String v = paramFields[i].getText().trim();
			if (!v.isEmpty() && p.isInt() && !diagram.macros.containsKey(v)) {
				try {
					Integer.parseInt(v);
				} catch (NumberFormatException e) {
					complain(p.key() + " must be an integer.", paramFields[i]);
					return;
				}
			}
		}
		block.name = name;
		for (int i = 0; i < paramFields.length; i++)
			block.params.put(params.get(i).key(), paramFields[i].getText().trim());
		diagram.dirty = true;
		changed = true;
		dispose();
	}

	private void complain(String message, JTextField field) {
		JOptionPane.showMessageDialog(this, message, "Invalid value", JOptionPane.WARNING_MESSAGE);
		field.requestFocusInWindow();
		field.selectAll();
	}
}
