package com.pistacium.modcheck;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pistacium.modcheck.mod.ModData;
import com.pistacium.modcheck.mod.resource.ModResource;
import com.pistacium.modcheck.mod.version.ModVersion;
import com.pistacium.modcheck.util.Config;
import com.pistacium.modcheck.util.ModCheckStatus;
import com.pistacium.modcheck.util.ModCheckUtils;
import com.pistacium.modcheck.util.SwingUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ModCheckFrame extends JFrame {

    private static final FontUIResource font = new FontUIResource("SansSerif", Font.BOLD, 15);

    private final JPanel mainPanel;
    private JProgressBar progressBar;
    private JComboBox<ModVersion> versionSelection;
    private JPanel versionJPanel;
    private JScrollPane versionScrollPane;
    private final HashMap<ModData, JCheckBox> modCheckBoxes = new HashMap<>();
    private JButton downloadButton;
    private File[] selectDirs = null;
    private JLabel selectedDirLabel;

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    ModCheckFrame() throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException,
            IllegalAccessException {
        super("ModCheck v" + ModCheckConstants.APPLICATION_VERSION + " by RedLime (Traduzido por Tchongass)");
        setUIFont();

        mainPanel = new JPanel(new BorderLayout());

        initHeaderLayout();

        initCenterLayout();

        initBottomLayout();

        initMenuBar();

        getContentPane().add(mainPanel);

        setSize(1100, 700);
        setResizable(false);
        setVisible(true);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        URL resource = getClass().getClassLoader().getResource("end_crystal.png");
        if (resource != null)
            setIconImage(new ImageIcon(resource).getImage());
    }

    private static void setUIFont() {
        Enumeration<?> keys = UIManager.getLookAndFeelDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource)
                UIManager.put(key, ModCheckFrame.font);
        }
    }

    private void initHeaderLayout() {
        JPanel instanceSelectPanel = new JPanel();

        JButton selectPathButton = new JButton("Selecione o caminho da instancia");
        selectPathButton.addActionListener(e -> {
            Config instanceDir = ModCheckUtils.readConfig();
            JFileChooser pathSelector = instanceDir == null ? new JFileChooser()
                    : new JFileChooser(instanceDir.getDir());
            pathSelector.setMultiSelectionEnabled(true);
            pathSelector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            pathSelector.setDialogType(JFileChooser.CUSTOM_DIALOG);
            pathSelector.setDialogTitle("Selecione o caminho da instancia");
            JComboBox<?> jComboBox = SwingUtils.getDescendantsOfType(JComboBox.class, pathSelector).get(0);
            jComboBox.setEditable(true);
            jComboBox.setEditor(new BasicComboBoxEditor.UIResource() {
                @Override
                public Object getItem() {
                    try {
                        return new File((String) super.getItem());
                    } catch (Exception e) {
                        return super.getItem();
                    }
                }
            });

            int showDialog = pathSelector.showDialog(this, "Selecione");
            File[] files = pathSelector.getSelectedFiles();
            if (pathSelector.getSelectedFiles() != null && showDialog == JFileChooser.APPROVE_OPTION) {
                selectDirs = files;
                String parentDir = "";
                StringBuilder stringBuilder = new StringBuilder();
                for (File selectDir : selectDirs) {
                    stringBuilder.append(
                            parentDir.isEmpty() ? selectDir.getPath() : selectDir.getPath().replace(parentDir, ""))
                            .append(", ");
                    parentDir = selectDir.getParent();
                }
                selectedDirLabel.setText("<html>Instancias Selecionadas : <br>"
                        + stringBuilder.substring(0, stringBuilder.length() - (stringBuilder.length() != 0 ? 2 : 0))
                        + "</html>");
            }
            ModCheckUtils.writeConfig(files[0].getParentFile());
        });

        instanceSelectPanel.add(selectPathButton);
        mainPanel.add(instanceSelectPanel, BorderLayout.NORTH);
    }

    private void initBottomLayout() {
        JPanel instanceBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 100, 5));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Esperando...");
        progressBar.setPreferredSize(new Dimension(500, 30));

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
        JCheckBox jCheckBox = new JCheckBox("Deletar todos .Jar antes de fazer Download");
        downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> {
            if (selectDirs == null || selectDirs.length < 1)
                return;

            downloadButton.setEnabled(false);
            Stack<File> modsFileStack = new Stack<>();

            int ignoreInstance = -1;

            for (File instanceDir : selectDirs) {
                Path instancePath = instanceDir.toPath();
                File dotMinecraft = instancePath.resolve(".minecraft").toFile();
                if (dotMinecraft.isDirectory()) {
                    instancePath = instancePath.resolve(".minecraft");
                }

                Path modsPath = instancePath.resolve("mods");
                File modsDir = modsPath.toFile();
                if (!modsDir.isDirectory()) {
                    int result = ignoreInstance != -1 ? ignoreInstance
                            : JOptionPane.showConfirmDialog(this,
                                    "Voce selecionou uma pasta, mas nao uma de minecraft.\\n" + //
                                            "Tem certeza que quer baixar aqui?",
                                    "Caminho errado", JOptionPane.OK_CANCEL_OPTION);

                    System.out.println(result);
                    if (result != 0) {
                        downloadButton.setEnabled(true);
                        return;
                    } else {
                        ignoreInstance = result;
                        modsFileStack.push(instanceDir);
                    }
                } else {
                    modsFileStack.push(modsDir);
                }
            }

            if (this.versionSelection.getSelectedItem() == null) {
                JOptionPane.showMessageDialog(this, "Error: Item selecionado vazio");
                downloadButton.setEnabled(true);
                return;
            }

            ArrayList<ModData> targetMods = new ArrayList<>();
            int maxCount = 0;
            for (Map.Entry<ModData, JCheckBox> modEntry : modCheckBoxes.entrySet()) {
                if (modEntry.getValue().isSelected() && modEntry.getValue().isEnabled()) {
                    System.out.println("Selected " + modEntry.getKey().getName());
                    targetMods.add(modEntry.getKey());
                    maxCount++;
                }
            }
            ModVersion mcVersion = (ModVersion) this.versionSelection.getSelectedItem();

            for (File instanceDir : modsFileStack) {
                File[] modFiles = instanceDir.listFiles();
                if (modFiles == null)
                    return;
                for (File file : modFiles) {
                    if (file.getName().endsWith(".jar")) {
                        if (jCheckBox.isSelected()) {
                            file.delete();
                        } else {
                            String modFileName = file.getName().split(ModVersion.versionRegex.pattern())[0]
                                    .split(ModVersion.snapshotRegex.pattern())[0];
                            for (ModData targetMod : targetMods) {
                                String targetModFileName = targetMod.getLatestVersionResource(mcVersion).getFileName();
                                if (targetModFileName.startsWith(modFileName)) {
                                    file.delete();
                                }
                            }
                        }
                    }
                }
            }

            this.progressBar.setValue(0);
            ModCheck.setStatus(ModCheckStatus.DOWNLOADING_MOD_FILE);

            int finalMaxCount = maxCount;
            ModCheck.THREAD_EXECUTOR.submit(() -> {
                int count = 0;
                ArrayList<ModData> failedMods = new ArrayList<>();
                for (ModData targetMod : targetMods) {
                    this.progressBar.setString("Fazendo Download de '" + targetMod.getName() + "'");
                    System.out.println("Fazendo Download de " + targetMod.getName());
                    Stack<File> downloadFiles = new Stack<>();
                    downloadFiles.addAll(modsFileStack);
                    if (!targetMod.downloadModJarFile(mcVersion, downloadFiles)) {
                        System.out.println("Falha no Download " + targetMod.getName());
                        failedMods.add(targetMod);
                    }
                    this.progressBar.setValue((int) ((++count / (finalMaxCount * 1f)) * 100));
                }
                this.progressBar.setValue(100);
                ModCheck.setStatus(ModCheckStatus.IDLE);

                System.out.println("Download completo");

                if (failedMods.size() > 0) {
                    StringBuilder failedModString = new StringBuilder();
                    for (ModData failedMod : failedMods) {
                        failedModString.append(failedMod.getName()).append(", ");
                    }
                    JOptionPane.showMessageDialog(this,
                            "Falha no download de " + failedModString.substring(0, failedModString.length() - 2) + ".",
                            "Por favor tenta denovo", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Foi feito o download de todos os mods!");
                }
                downloadButton.setEnabled(true);
            });
        });
        downloadButton.setEnabled(false);

        instanceBottomPanel.add(progressBar);
        buttonPanel.add(jCheckBox);
        buttonPanel.add(downloadButton);
        instanceBottomPanel.add(buttonPanel);
        mainPanel.add(instanceBottomPanel, BorderLayout.SOUTH);
    }

    private void initCenterLayout() {
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JPanel instancePathsPanel = new JPanel();
        selectedDirLabel = new JLabel("Voce pode selecionar varias instancias apertando Shift ou Ctrl");
        instancePathsPanel.add(selectedDirLabel);

        JPanel versionSelectPanel = new JPanel();
        JLabel headerLabel = new JLabel("Versao do Minecraft: ");
        versionSelection = new JComboBox<>();
        versionSelection.addActionListener(e -> updateModList());

        versionJPanel = new JPanel();
        versionJPanel.setLayout(new BoxLayout(versionJPanel, BoxLayout.Y_AXIS));
        versionScrollPane = new JScrollPane(versionJPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        versionScrollPane.setPreferredSize(new Dimension(1000, 500));
        versionScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel selectButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton selectAllButton = new JButton("Marcar Recomendados");
        selectAllButton.addActionListener(e -> {
            for (Map.Entry<ModData, JCheckBox> entry : modCheckBoxes.entrySet()) {
                if (!entry.getKey().getReadme().isEmpty() || entry.getKey().getIncompatibleMods().size() > 0)
                    continue;

                if (entry.getValue().isEnabled() && entry.getKey().getWarningMessage().isEmpty()) {
                    entry.getValue().setSelected(true);
                }
            }
            JOptionPane.showMessageDialog(this,
                    "<html><body>Alguns mods nao sao selecionados automaticamente por nao serem compativeis uns com os outros (Starlight e Phosphor)<br>\r\n"
                            + //
                            "Alguns sao APENAS para a Tabela Brasileira, e nao sao aceitos no speedrun.com\r\n" + //
                            "Alguns sao por alguma outra razao que voce pode ver selecionando ele\r\n" + //
                            " esses mods voce precisa selecionar manualmente",
                    "Cuidado!", JOptionPane.WARNING_MESSAGE);
        });
        JButton deselectAllButton = new JButton("Desmarcar todos");
        deselectAllButton.addActionListener(e -> {
            for (JCheckBox cb : modCheckBoxes.values()) {
                cb.setSelected(false);
                cb.setEnabled(true);
            }
        });
        selectButtonPanel.add(selectAllButton);
        selectButtonPanel.add(deselectAllButton);

        versionSelectPanel.add(headerLabel);
        versionSelectPanel.add(versionSelection);
        centerPanel.add(instancePathsPanel);
        centerPanel.add(versionSelectPanel);
        centerPanel.add(selectButtonPanel);
        centerPanel.add(versionScrollPane);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
        mainPanel.add(centerPanel, BorderLayout.CENTER);
    }

    public void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu source = new JMenu("Mais informacoes");

        JMenuItem githubSource = new JMenuItem("Ranking Brasileiro");
        githubSource.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(
                        "https://docs.google.com/spreadsheets/d/1ovMyHrmk0J4BumPygSigTScmt7T-saKqaHrPCjSQG-Y/edit?usp=sharing"));
            } catch (Exception ignored) {
            }
        });
        source.add(githubSource);

        JMenuItem discordSource = new JMenuItem("Discord Brasileiro");
        discordSource.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://discord.gg/wshxtk64Jj"));
            } catch (Exception ignored) {
            }
        });
        source.add(discordSource);

        JMenuItem checkChangeLogSource = new JMenuItem("Outros Recursos");
        checkChangeLogSource.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(
                        "https://www.minecraftspeedrunning.com/public-resources"));
            } catch (Exception ignored) {
            }
        });
        source.add(checkChangeLogSource);

        JMenuItem updateCheckSource = new JMenuItem("Versao original");
        updateCheckSource.addActionListener(e -> {
            try {
                JsonObject jsonObject = JsonParser
                        .parseString(ModCheckUtils
                                .getUrlRequest("https://api.github.com/repos/RedLime/ModCheck/releases/latest"))
                        .getAsJsonObject();
                if (ModVersion.of(jsonObject.get("tag_name").getAsString())
                        .compareTo(ModVersion.of(ModCheckConstants.APPLICATION_VERSION)) > 0) {
                    int result = JOptionPane.showOptionDialog(null,
                            "<html><body>Se quiser a versao gringa baixa isso ai<br><br>Current Version : "
                                    + ModCheckConstants.APPLICATION_VERSION + "<br>Updated Version : "
                                    + jsonObject.get("tag_name").getAsString() + "</body></html>",
                            "Update Checker", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                            new String[] { "Download", "Cancel" }, "Download");
                    if (result == 0) {
                        Desktop.getDesktop().browse(new URI("https://github.com/RedLime/ModCheck/releases/latest"));
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "You are using the latest version!");
                }
            } catch (Exception ignored) {
            }
        });
        source.add(updateCheckSource);

        menuBar.add(source);

        this.setJMenuBar(menuBar);
    }

    public void updateVersionList() {
        versionSelection.removeAllItems();
        for (ModVersion availableVersion : ModCheck.AVAILABLE_VERSIONS) {
            versionSelection.addItem(availableVersion);
        }
        versionSelection.setSelectedItem(ModCheck.AVAILABLE_VERSIONS.get(0));
        updateModList();
    }

    public void updateModList() {
        versionJPanel.removeAll();
        modCheckBoxes.clear();

        if (this.versionSelection.getSelectedItem() == null)
            return;

        ModVersion mcVersion = (ModVersion) this.versionSelection.getSelectedItem();

        for (ModData modData : ModCheck.AVAILABLE_MODS) {
            ModResource modResource = modData.getLatestVersionResource(mcVersion);
            if (modResource != null) {
                JPanel modPanel = new JPanel();
                modPanel.setLayout(new BoxLayout(modPanel, BoxLayout.Y_AXIS));

                String versionName = modResource.getModVersion().getVersionName();
                JCheckBox checkBox = new JCheckBox(
                        modData.getName() + " (v" + (versionName.substring(versionName.startsWith("v") ? 1 : 0)) + ")");
                checkBox.addActionListener(i -> {
                    boolean isSelected = checkBox.isSelected();
                    for (String incompatibleMod : modData.getIncompatibleMods()) {
                        for (Map.Entry<ModData, JCheckBox> entry : modCheckBoxes.entrySet()) {
                            if (Objects.equals(entry.getKey().getName(), incompatibleMod)) {
                                entry.getValue().setEnabled(!isSelected);
                            }
                        }
                    }

                    if (isSelected && !modData.getWarningMessage().isEmpty()) {
                        JOptionPane.showMessageDialog(this, "<html><body>" + modData.getWarningMessage()
                                + "<br>Se voce ignorar esse aviso sua run pode ser rejeitada no Speedrun.com</body></html>",
                                "Cuidado!", JOptionPane.WARNING_MESSAGE);
                    }

                    if (isSelected && !modData.getReadme().isEmpty()) {
                        Object[] options = { "Ler o Readme", "EU SEI!", "Cancelar" };
                        int result = JOptionPane.showOptionDialog(this,
                                "Se estiver usando esse mod pela primeira vez, procure ajuda", "CUIDADO!",
                                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                        if (result == 0) {
                            try {
                                Desktop.getDesktop().browse(new URI(modData.getReadme()));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (result == 2) {
                            checkBox.setSelected(false);
                        }
                    }
                });

                int line = modData.getDescription().split("\n").length;
                JLabel description = new JLabel("<html><body>" + modData.getDescription().replaceAll("\n", "<br>")
                        .replaceAll("<a ", "<b ").replaceAll("</a>", "</b>") + "</body></html>");
                description.setMaximumSize(new Dimension(800, 60 * line));
                description.setBorder(new EmptyBorder(0, 15, 0, 0));
                Font f = description.getFont();
                description.setFont(f.deriveFont(f.getStyle() & ~Font.BOLD));

                modPanel.add(checkBox);
                modPanel.add(description);
                modPanel.setMaximumSize(new Dimension(950, 60 * line));
                modPanel.setBorder(new EmptyBorder(0, 10, 10, 0));

                versionJPanel.add(modPanel);
                modCheckBoxes.put(modData, checkBox);
            }
        }
        versionJPanel.updateUI();
        versionScrollPane.updateUI();
        downloadButton.setEnabled(true);
    }
}
