package com.collectionselection;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CollectionSelectionPanel extends PluginPanel
{
	// ── Palette ───────────────────────────────────────────────────────────────
	private static final Color COL_GOLD         = new Color(212, 175, 55);
	private static final Color COL_GOLD_BRIGHT  = new Color(255, 215,  0);
	private static final Color COL_GOLD_DIM     = new Color( 90,  72, 18);

	private static final Color COL_ACTIVE_STRIP = new Color(190, 150, 30);
	private static final Color COL_ACTIVE_SEL   = new Color( 46,  36, 10);
	private static final Color COL_ACTIVE_FG    = new Color(230, 215, 160);

	private static final Color COL_LOCKED_STRIP = new Color(160,  40, 40);
	private static final Color COL_LOCKED_SEL   = new Color( 52,  16, 16);
	private static final Color COL_LOCKED_FG    = new Color(220,  80, 80);

	private static final Color COL_ROW          = new Color( 30,  30, 30);
	private static final Color COL_ROW_HOV      = new Color( 42,  42, 42);
	private static final Color COL_ITEM_FG      = new Color(140, 133, 115);
	private static final Color COL_DIVIDER      = new Color( 45,  45, 45);
	private static final Color COL_HEADER_BG    = new Color( 14,  11,  3);
	private static final Color COL_SECTION_BG   = new Color( 22,  22, 22);

	private static final Color COL_BTN_COLLECT  = new Color(30,  70,  30);
	private static final Color COL_BTN_PET      = new Color(30,  50, 100);
	private static final Color COL_BTN_CLOG     = new Color(30,  60,  60);
	private static final Color COL_BTN_DISABLED = new Color(45,  45,  45);

	// ── State ─────────────────────────────────────────────────────────────────
	private JPanel entriesPanel;
	private final Map<JPanel, String> entryPanelMap = new LinkedHashMap<>();
	private final Set<String>         lockedNames   = new HashSet<>();
	private JPanel selectedRow       = null;
	private String selectedEntryName = null;

	// ── Fixed components ──────────────────────────────────────────────────────
	private final JTextField testItemField     = new JTextField();
	private final JButton    simulateCollectBtn;
	private final JButton    simulatePetBtn;
	private final JButton    simulateClogBtn;

	private Consumer<String> onSimulateCollect;
	private Runnable         onSimulatePet;
	private Consumer<String> onSimulateClogPopup;

	private final ImageIcon lockIconSmall;
	private JComponent      testSection;

	public CollectionSelectionPanel()
	{
		ImageIcon icon = null;
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(getClass(), "/locked_icon.png");
			icon = new ImageIcon(img.getScaledInstance(10, 10, Image.SCALE_SMOOTH));
		}
		catch (Exception ignored) {}
		lockIconSmall = icon;

		simulateCollectBtn = createButton("SIMULATE COLLECT",    COL_BTN_COLLECT, COL_BTN_COLLECT.brighter());
		simulatePetBtn     = createButton("SIMULATE PET",        COL_BTN_PET,     COL_BTN_PET.brighter());
		simulateClogBtn    = createButton("SIMULATE CLOG POPUP", COL_BTN_CLOG,    COL_BTN_CLOG.brighter());

		setLayout(new BorderLayout(0, 0));
		setBackground(COL_HEADER_BG);

		testSection = buildTestSection();
		testSection.setVisible(false);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(testSection,   BorderLayout.SOUTH);
	}

	// ── Header ────────────────────────────────────────────────────────────────

	private JComponent buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout(0, 4))
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setPaint(new GradientPaint(0, 0, new Color(26, 20, 6), 0, getHeight(), COL_HEADER_BG));
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(new Color(COL_GOLD.getRed(), COL_GOLD.getGreen(), COL_GOLD.getBlue(), 180));
				g2.fillRect(0, 0, 2, getHeight());
				g2.fillRect(getWidth() - 2, 0, 2, getHeight());
				g2.setColor(COL_GOLD_DIM);
				g2.fillRect(0, getHeight() - 1, getWidth(), 1);
				g2.dispose();
			}
		};
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(8, 10, 8, 10));

		Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 13f);

		JLabel titleMain = new JLabel("COLLECTION SELECTIO");
		titleMain.setFont(titleFont);
		titleMain.setForeground(COL_GOLD_BRIGHT);

		// "N" — hidden dev-mode trigger, looks like plain title text
		JLabel titleN = new JLabel("N");
		titleN.setFont(titleFont);
		titleN.setForeground(COL_GOLD_BRIGHT);
		titleN.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				String pw = (String) JOptionPane.showInputDialog(
					CollectionSelectionPanel.this, null, "Dev Mode",
					JOptionPane.PLAIN_MESSAGE, null, null, null);
				if ("lockout".equals(pw))
				{
					testSection.setVisible(!testSection.isVisible());
					revalidate();
					repaint();
				}
			}
		});

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		titleRow.setOpaque(false);
		titleRow.add(titleMain);
		titleRow.add(titleN);

		header.add(titleRow, BorderLayout.NORTH);
		return header;
	}

	// ── Entry list ────────────────────────────────────────────────────────────

	private JComponent buildCenter()
	{
		entriesPanel = new JPanel();
		entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
		entriesPanel.setBackground(COL_ROW);
		entriesPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, COL_GOLD_DIM));

		JPanel center = new JPanel(new BorderLayout(0, 5));
		center.setBackground(COL_HEADER_BG);
		center.setBorder(new EmptyBorder(5, 4, 5, 4));
		center.add(entriesPanel, BorderLayout.CENTER);
		return center;
	}

	// ── Test section (dev-only) ───────────────────────────────────────────────

	private JComponent buildTestSection()
	{
		JLabel testLabel = new JLabel("TEST MODE", SwingConstants.CENTER);
		testLabel.setFont(FontManager.getRunescapeSmallFont());
		testLabel.setForeground(new Color(70, 70, 70));

		testItemField.setBackground(new Color(30, 30, 30));
		testItemField.setForeground(Color.LIGHT_GRAY);
		testItemField.setCaretColor(Color.WHITE);
		testItemField.setFont(FontManager.getRunescapeSmallFont());
		testItemField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(55, 55, 55)),
			new EmptyBorder(2, 5, 2, 5)));

		simulateCollectBtn.addActionListener(e ->
		{
			String name = testItemField.getText().trim();
			if (!name.isEmpty() && onSimulateCollect != null) onSimulateCollect.accept(name);
		});
		simulatePetBtn.addActionListener(e ->
		{
			if (onSimulatePet != null) onSimulatePet.run();
		});
		simulateClogBtn.addActionListener(e ->
		{
			String name = testItemField.getText().trim();
			if (!name.isEmpty() && onSimulateClogPopup != null) onSimulateClogPopup.accept(name);
		});

		JPanel section = new JPanel(new GridLayout(5, 1, 0, 4));
		section.setBackground(new Color(18, 18, 18));
		section.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 0, 0, 0, new Color(50, 50, 50)),
			new EmptyBorder(7, 7, 7, 7)));
		section.add(testLabel);
		section.add(testItemField);
		section.add(simulateCollectBtn);
		section.add(simulatePetBtn);
		section.add(simulateClogBtn);
		return section;
	}

	// ── Callbacks ─────────────────────────────────────────────────────────────

	public void setOnSimulateCollect(Consumer<String> cb)  { this.onSimulateCollect  = cb; }
	public void setOnSimulatePet(Runnable cb)              { this.onSimulatePet      = cb; }
	public void setOnSimulateClogPopup(Consumer<String> cb){ this.onSimulateClogPopup = cb; }

	public String getSelectedEntryName() { return selectedEntryName; }

	// ── Data updates ──────────────────────────────────────────────────────────

	public void refresh(Map<String, CollectionSelectionPlugin.TargetEntry> targets, boolean hasPetCredit)
	{
		SwingUtilities.invokeLater(() ->
		{
			lockedNames.clear();
			entryPanelMap.clear();
			selectedRow       = null;
			selectedEntryName = null;
			entriesPanel.removeAll();

			List<CollectionSelectionPlugin.TargetEntry> active = new ArrayList<>();
			List<CollectionSelectionPlugin.TargetEntry> locked = new ArrayList<>();

			for (CollectionSelectionPlugin.TargetEntry e : targets.values())
			{
				if (e.locked) { locked.add(e); lockedNames.add(e.entryName); }
				else            active.add(e);
			}

			if (!active.isEmpty())
			{
				entriesPanel.add(makeSectionHeader("ACTIVE TARGETS", COL_GOLD));
				for (CollectionSelectionPlugin.TargetEntry e : active) entriesPanel.add(makeEntryRow(e));
			}

			if (!locked.isEmpty())
			{
				entriesPanel.add(makeSectionHeader("LOCKED PAGES", COL_LOCKED_FG));
				for (CollectionSelectionPlugin.TargetEntry e : locked) entriesPanel.add(makeEntryRow(e));
			}

			entriesPanel.revalidate();
			entriesPanel.repaint();
		});
	}

	// ── Row helpers ───────────────────────────────────────────────────────────

	private JComponent makeSectionHeader(String text, Color accent)
	{
		JPanel hdr = new JPanel(new BorderLayout())
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(COL_SECTION_BG);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(accent);
				g2.fillRect(0, 0, 3, getHeight());
				g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
				g2.fillRect(0, getHeight() - 1, getWidth(), 1);
				g2.dispose();
			}
		};
		hdr.setOpaque(false);
		hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
		hdr.setPreferredSize(new Dimension(0, 15));

		JLabel lbl = new JLabel(text);
		lbl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		lbl.setForeground(accent);
		lbl.setBorder(new EmptyBorder(0, 8, 0, 0));
		hdr.add(lbl, BorderLayout.CENTER);
		return hdr;
	}

	private JPanel makeEntryRow(CollectionSelectionPlugin.TargetEntry entry)
	{
		boolean locked = entry.locked;

		Color stripCol = locked ? COL_LOCKED_STRIP : COL_ACTIVE_STRIP;
		Color selectBg = locked ? COL_LOCKED_SEL   : COL_ACTIVE_SEL;
		Color entryFg  = locked ? COL_LOCKED_FG    : COL_ACTIVE_FG;

		JLabel iconLbl = new JLabel();
		if (locked && lockIconSmall != null) iconLbl.setIcon(lockIconSmall);
		iconLbl.setPreferredSize(new Dimension(12, 30));
		iconLbl.setVerticalAlignment(SwingConstants.CENTER);

		JLabel entryLbl = new JLabel(entry.entryName);
		entryLbl.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		entryLbl.setForeground(entryFg);

		JLabel itemLbl = new JLabel(entry.itemName);
		itemLbl.setFont(FontManager.getRunescapeSmallFont());
		itemLbl.setForeground(COL_ITEM_FG);

		JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
		text.setOpaque(false);
		text.add(entryLbl);
		text.add(itemLbl);

		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setOpaque(true);
		row.setBackground(COL_ROW);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				new MatteBorder(0, 3, 0, 0, stripCol),
				new MatteBorder(0, 0, 1, 0, COL_DIVIDER)
			),
			new EmptyBorder(3, 7, 3, 6)
		));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		row.setPreferredSize(new Dimension(0, 30));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.add(iconLbl, BorderLayout.WEST);
		row.add(text,    BorderLayout.CENTER);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e) { selectRow(row, entry.entryName); }
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (row != selectedRow) { row.setBackground(COL_ROW_HOV); row.repaint(); }
			}
			@Override
			public void mouseExited(MouseEvent e)
			{
				if (row != selectedRow) { row.setBackground(COL_ROW); row.repaint(); }
			}
		});

		entryPanelMap.put(row, entry.entryName);
		return row;
	}

	private void selectRow(JPanel row, String entryName)
	{
		if (selectedRow != null)
		{
			selectedRow.setBackground(COL_ROW);
			selectedRow.repaint();
		}
		selectedRow       = row;
		selectedEntryName = entryName;
		row.setBackground(lockedNames.contains(entryName) ? COL_LOCKED_SEL : COL_ACTIVE_SEL);
		row.repaint();
	}

	// ── Button factory ────────────────────────────────────────────────────────

	private static JButton createButton(String text, Color base, Color hover)
	{
		JButton btn = new JButton(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color bg = !isEnabled()            ? COL_BTN_DISABLED
					     : getModel().isPressed()  ? base.darker()
					     : getModel().isRollover() ? hover
					     : base;

				g2.setColor(bg);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);

				if (isEnabled())
				{
					g2.setColor(new Color(255, 255, 255, 20));
					g2.fillRoundRect(0, 0, getWidth() - 1, (getHeight() - 1) / 2, 5, 5);
					g2.setColor(bg.darker());
				}
				else
				{
					g2.setColor(new Color(65, 65, 65));
				}
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
				g2.dispose();
				super.paintComponent(g);
			}

			@Override
			public void setEnabled(boolean b)
			{
				super.setEnabled(b);
				setForeground(b ? Color.WHITE : new Color(90, 90, 90));
				repaint();
			}
		};

		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setFocusPainted(false);
		btn.setForeground(Color.WHITE);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setPreferredSize(new Dimension(0, 22));

		btn.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
			@Override public void mouseExited(MouseEvent e)  { btn.repaint(); }
		});

		return btn;
	}
}
