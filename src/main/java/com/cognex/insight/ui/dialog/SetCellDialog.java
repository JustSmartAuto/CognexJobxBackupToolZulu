package com.cognex.insight.ui.dialog;

import javax.swing.*;
import java.awt.*;

public class SetCellDialog extends JDialog {
    private boolean confirmed = false;
    public final JTextField txtCell;
    public final JTextField txtValue;

    public SetCellDialog(java.awt.Window parent, String title, String cellLabel, String valueLabel) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setSize(320, 160);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel lblCell = new JLabel(cellLabel + ":");
        lblCell.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        txtCell = new JTextField("A3", 12);
        txtCell.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        JLabel lblValue = new JLabel(valueLabel + ":");
        lblValue.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        txtValue = new JTextField("2", 12);
        txtValue.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        inputPanel.add(lblCell);
        inputPanel.add(txtCell);
        inputPanel.add(lblValue);
        inputPanel.add(txtValue);

        add(inputPanel, BorderLayout.CENTER);

        JButton btnOk = new JButton("确定");
        btnOk.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnOk.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JButton btnCancel = new JButton("取消");
        btnCancel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnCancel.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
