import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Aplicación Windows, sin guardar las capturas realizadas durante OCR. */
public class LectorPantalla {
    private static final int WM_HOTKEY = 0x0312;
    private static final int HOTKEY_ID = 1;
    private static final int MOD_ALT = 0x0001, MOD_CONTROL = 0x0002, MOD_SHIFT = 0x0004, MOD_WIN = 0x0008;
    private static final Preferences PREFS = Preferences.userRoot().node("AppleAudio");
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();
    private static final Map<String, VoiceProfile> PIPER_VOICES = new LinkedHashMap<>();
    private static final Map<String, String> EDGE_VOICES = new LinkedHashMap<>();
    private record VoiceProfile(String model, int speaker) { }
    static {
        LANGUAGES.put("Español", "spa");
        PIPER_VOICES.put("España - Sara", new VoiceProfile("es_ES-sharvard-medium.onnx", 1));
        PIPER_VOICES.put("España - Hugo", new VoiceProfile("es_ES-sharvard-medium.onnx", 0));
        PIPER_VOICES.put("México - Claude", new VoiceProfile("es_MX-claude-high.onnx", 0));
        PIPER_VOICES.put("México - Ald", new VoiceProfile("es_MX-ald-medium.onnx", 0));
        EDGE_VOICES.put("Colombia - Salomé (en línea)", "es-CO-SalomeNeural");
        EDGE_VOICES.put("Colombia - Gonzalo (en línea)", "es-CO-GonzaloNeural");
    }

    private JFrame frame;
    private JTextField shortcutField;
    private JComboBox<String> languageBox, voiceBox;
    private int speechRate;
    private JLabel status;
    private volatile int modifiers, virtualKey;
    private volatile Process speaking;
    private volatile long shortcutRevision;
    private volatile boolean hotkeyThreadStarted;
    private volatile boolean captureInProgress;

    public static void main(String[] args) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(null, "Esta aplicación está diseñada para Windows.", "Lector de pantalla", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new LectorPantalla().show());
    }

    private void show() {
        configureAppearance();
        restoreShortcut();
        frame = new JFrame("AppleAudio");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setIconImage(createAppIcon());
        frame.setContentPane(createContent());
        frame.pack(); frame.setResizable(false);
        frame.setLocationRelativeTo(null); frame.setVisible(true);
        installTraySupport();
        startHotkeyListener();
        loadVoices();
    }

    private JPanel createContent() {
        Color canvas=new Color(12,15,20), surface=new Color(20,24,32), elevated=new Color(28,34,45), primary=new Color(117,232,185), secondary=new Color(164,174,191);
        JPanel root=new JPanel(new BorderLayout());root.setBackground(canvas);root.setBorder(BorderFactory.createEmptyBorder(22,22,22,22));
        RoundedPanel shell=new RoundedPanel(24,surface);shell.setLayout(new BorderLayout());root.add(shell);
        RoundedPanel sidebar=new RoundedPanel(24,new Color(17,21,28));sidebar.setLayout(new BoxLayout(sidebar,BoxLayout.Y_AXIS));sidebar.setBorder(BorderFactory.createEmptyBorder(28,25,26,25));sidebar.setPreferredSize(new Dimension(205,0));
        JLabel brand=label("AppleAudio",19,Font.BOLD,Color.WHITE); JLabel brandSub=label("Lector de pantalla",11,Font.PLAIN,new Color(151,161,178));sidebar.add(brand);sidebar.add(Box.createVerticalStrut(5));sidebar.add(brandSub);sidebar.add(Box.createVerticalGlue());
        RoundedPanel ready=new RoundedPanel(14,new Color(25,46,42));ready.setLayout(new BoxLayout(ready,BoxLayout.Y_AXIS));ready.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));ready.setMaximumSize(new Dimension(160,80));
        ready.add(label("Listo",11,Font.BOLD,primary));ready.add(Box.createVerticalStrut(4));ready.add(label("F8 para seleccionar",11,Font.PLAIN,new Color(183,206,199)));sidebar.add(ready);shell.add(sidebar,BorderLayout.WEST);
        JPanel content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));content.setBorder(BorderFactory.createEmptyBorder(31,35,27,35));shell.add(content,BorderLayout.CENTER);
        JLabel eyebrow=label("Lectura instantánea",11,Font.BOLD,primary); JLabel title=label("Tu pantalla, en voz.",30,Font.BOLD,Color.WHITE); JLabel subtitle=label("Selecciona el fragmento que importa y escúchalo.",14,Font.PLAIN,secondary);
        content.add(eyebrow);content.add(Box.createVerticalStrut(8));content.add(title);content.add(Box.createVerticalStrut(6));content.add(subtitle);content.add(Box.createVerticalStrut(25));
        RoundedPanel shortcutCard=new RoundedPanel(18,elevated);shortcutCard.setLayout(new BorderLayout(18,0));shortcutCard.setBorder(BorderFactory.createEmptyBorder(17,19,17,19));shortcutCard.setMaximumSize(new Dimension(600,84));
        JPanel intro=new JPanel();intro.setOpaque(false);intro.setLayout(new BoxLayout(intro,BoxLayout.Y_AXIS));intro.add(label("Capturar y leer",11,Font.BOLD,primary));intro.add(Box.createVerticalStrut(4));intro.add(label("Pulsa el atajo para marcar texto",14,Font.BOLD,Color.WHITE));shortcutCard.add(intro,BorderLayout.CENTER);
        shortcutField = new JTextField(9);
        shortcutField.setText(comboText()); shortcutField.setEditable(false);
        styleField(shortcutField);shortcutField.setHorizontalAlignment(SwingConstants.CENTER);shortcutField.setPreferredSize(new Dimension(120,42));shortcutField.setForeground(Color.WHITE);shortcutField.setBackground(new Color(42,51,66)); shortcutField.setToolTipText("Haz clic aquí y presiona la tecla o combinación");
        shortcutField.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { captureShortcut(e); } });
        shortcutCard.add(shortcutField,BorderLayout.EAST);content.add(shortcutCard);content.add(Box.createVerticalStrut(23));
        JPanel settings=new JPanel(new GridLayout(1,2,14,0));settings.setOpaque(false);settings.setMaximumSize(new Dimension(600,106));
        languageBox = new JComboBox<>(LANGUAGES.keySet().toArray(String[]::new));
        languageBox.setSelectedItem(PREFS.get("language", "Español"));
        languageBox.addActionListener(e -> PREFS.put("language", (String) languageBox.getSelectedItem()));
        styleField(languageBox); settings.add(settingCard("IDIOMA",languageBox,elevated,secondary));
        voiceBox = new JComboBox<>(); PIPER_VOICES.keySet().forEach(voiceBox::addItem); EDGE_VOICES.keySet().forEach(voiceBox::addItem);
        voiceBox.addActionListener(e -> PREFS.put("voice", String.valueOf(voiceBox.getSelectedItem())));
        styleField(voiceBox);settings.add(settingCard("VOZ",voiceBox,elevated,secondary));content.add(settings);content.add(Box.createVerticalStrut(18));
        RoundedPanel rateCard=new RoundedPanel(18,elevated);rateCard.setLayout(new BorderLayout(15,0));rateCard.setBorder(BorderFactory.createEmptyBorder(13,18,12,18));rateCard.setMaximumSize(new Dimension(600,72));rateCard.add(label("Velocidad",11,Font.BOLD,secondary),BorderLayout.WEST);
        rateCard.add(createSpeedControl(),BorderLayout.CENTER);content.add(rateCard);content.add(Box.createVerticalStrut(20));
        status=label("Preparado para leer una selección",13,Font.PLAIN,primary);content.add(status);return root;
    }
    private JPanel settingCard(String title,JComponent field,Color fill,Color muted){RoundedPanel card=new RoundedPanel(18,fill);card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));card.setBorder(BorderFactory.createEmptyBorder(13,16,13,16));card.add(label(title,11,Font.BOLD,muted));card.add(Box.createVerticalStrut(7));field.setMaximumSize(new Dimension(270,36));field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));card.add(field);return card;}
    private JLabel label(String text,int size,int style,Color color){JLabel label=new JLabel(text);label.setFont(new Font("Segoe UI",style,size));label.setForeground(color);label.setAlignmentX(Component.LEFT_ALIGNMENT);return label;}
    private void styleField(JComponent field){ field.setFont(new Font("Segoe UI",Font.PLAIN,13));field.setBorder(BorderFactory.createEmptyBorder(6,12,6,10));field.setBackground(new Color(42,51,66));field.setForeground(Color.WHITE); if(field instanceof JComboBox<?> combo){combo.setUI(new ModernComboBoxUI());combo.setToolTipText("Haz clic para elegir una opción");} }
    private JPanel createSpeedControl() {
        speechRate=PREFS.getInt("speed",0);
        JPanel control=new JPanel(new GridLayout(1,3,2,0));control.setOpaque(false);ButtonGroup group=new ButtonGroup();
        control.add(speedButton("Lenta",-4,group)); control.add(speedButton("Normal",0,group)); control.add(speedButton("Rápida",4,group));
        return control;
    }
    private JToggleButton speedButton(String text,int rate,ButtonGroup group) {
        JToggleButton button=new JToggleButton(text);button.setFont(new Font("Segoe UI",Font.PLAIN,12));button.setFocusPainted(false);button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));button.setSelected(speechRate==rate);button.setToolTipText("Velocidad " + text.toLowerCase());
        button.addActionListener(e->{speechRate=rate;PREFS.putInt("speed",rate);});group.add(button);return button;
    }
    private void configureAppearance() {
        FlatMacDarkLaf.setup();
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ComboBox.arc", 12);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", new Color(117, 232, 185));
        UIManager.put("ComboBox.popupBackground", new Color(36,43,56));
        UIManager.put("ComboBox.selectionBackground", new Color(55,112,94));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ToggleButton.selectedBackground", new Color(65, 131, 106));
        UIManager.put("ToggleButton.selectedForeground", Color.WHITE);
    }
    private Image createAppIcon() { BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setPaint(new GradientPaint(0,0,new Color(35,50,81),256,256,new Color(18,27,45)));g.fillRoundRect(4,4,248,248,62,62);g.setColor(new Color(119,235,190));int[] heights={48,84,120,84,48};for(int i=0;i<heights.length;i++){int h=heights[i];g.fillRoundRect(65+i*27,128-h/2,14,h,7,7);}g.dispose();return image; }
    private void installTraySupport() {
        if (!SystemTray.isSupported()) { frame.addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e){ frame.setVisible(false); }}); return; }
        PopupMenu menu=new PopupMenu(); MenuItem show=new MenuItem("Mostrar AppleAudio"), exit=new MenuItem("Salir"); menu.add(show);menu.add(exit);
        TrayIcon icon=new TrayIcon(createAppIcon(),"AppleAudio",menu);icon.setImageAutoSize(true);
        show.addActionListener(e->showWindow());icon.addActionListener(e->showWindow());exit.addActionListener(e->System.exit(0));
        frame.addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e){ frame.setVisible(false);icon.displayMessage("AppleAudio","Sigue activo en segundo plano.",TrayIcon.MessageType.NONE); }});
        try { SystemTray.getSystemTray().add(icon); } catch (AWTException ignored) { }
    }
    private void showWindow(){ frame.setVisible(true);frame.setExtendedState(JFrame.NORMAL);frame.toFront();frame.requestFocus(); }
    private static class ModernComboBoxUI extends BasicComboBoxUI {
        @Override protected JButton createArrowButton(){ return new JButton(){ {setBorder(BorderFactory.createEmptyBorder());setContentAreaFilled(false);setFocusable(false);setPreferredSize(new Dimension(32,30));} @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(new Color(181,193,211));g2.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));int x=getWidth()/2,y=getHeight()/2-2;g2.drawLine(x-5,y,x,y+5);g2.drawLine(x,y+5,x+5,y);g2.dispose();} }; }
    }
        private static class RoundedPanel extends JPanel { private final int radius; private final Color fill; RoundedPanel(int radius,Color fill){this.radius=radius;this.fill=fill;setOpaque(false);} @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(fill);g2.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);g2.dispose();super.paintComponent(g);} }
    private void restoreShortcut() {
        modifiers=PREFS.getInt("modifiers",MOD_CONTROL|MOD_SHIFT); virtualKey=PREFS.getInt("key",KeyEvent.VK_R);
    }
    private void captureShortcut(KeyEvent e) {
        int key=e.getKeyCode(); if (key==KeyEvent.VK_CONTROL||key==KeyEvent.VK_SHIFT||key==KeyEvent.VK_ALT||key==KeyEvent.VK_WINDOWS) return;
        int mods=0; int m=e.getModifiersEx();
        if ((m&InputEvent.CTRL_DOWN_MASK)!=0) mods|=MOD_CONTROL;
        if ((m&InputEvent.SHIFT_DOWN_MASK)!=0) mods|=MOD_SHIFT;
        if ((m&InputEvent.ALT_DOWN_MASK)!=0) mods|=MOD_ALT;
        if ((m&InputEvent.META_DOWN_MASK)!=0) mods|=MOD_WIN;
        modifiers=mods;virtualKey=key;PREFS.putInt("modifiers",mods);PREFS.putInt("key",key);shortcutField.setText(comboText());shortcutRevision++;setStatus("Atajo actualizado.",false);
    }
    private String comboText() {
        StringBuilder b=new StringBuilder(); if((modifiers&MOD_CONTROL)!=0)b.append("Ctrl + ");if((modifiers&MOD_ALT)!=0)b.append("Alt + ");if((modifiers&MOD_SHIFT)!=0)b.append("Mayús + ");if((modifiers&MOD_WIN)!=0)b.append("Windows + ");return b.append(KeyEvent.getKeyText(virtualKey)).toString();
    }
    private synchronized void startHotkeyListener() {
        if (hotkeyThreadStarted) return;
        hotkeyThreadStarted=true;
        Thread listener=new Thread(() -> {
            long registeredRevision=-1;
            boolean registered=false;
            MSG msg=new MSG();
            while(true) {
                if(registeredRevision!=shortcutRevision) {
                    if(registered) User32.INSTANCE.UnregisterHotKey(null,HOTKEY_ID);
                    registered=User32.INSTANCE.RegisterHotKey(null,HOTKEY_ID,modifiers,virtualKey);
                    registeredRevision=shortcutRevision;
                    boolean ok=registered;
                    SwingUtilities.invokeLater(() -> setStatus(ok?"Pulsa el atajo y arrastra el texto que quieras leer.":"Windows no pudo registrar ese atajo; prueba otro.",!ok));
                }
                while(User32.INSTANCE.PeekMessage(msg,null,0,0,1)) { // PM_REMOVE
                    if(msg.message==WM_HOTKEY&&msg.wParam.intValue()==HOTKEY_ID) readScreen();
                }
                try { Thread.sleep(20); } catch (InterruptedException ignored) { return; }
            }
        },"hotkey-listener");
        listener.setDaemon(true); listener.start();
    }
    private void readScreen() {
        synchronized (this) { if (captureInProgress) return; captureInProgress=true; }
        SwingUtilities.invokeLater(() -> setStatus("Selecciona con el ratón el área que quieres leer.",false));
        new Thread(() -> { Path image=null; try {
            Rectangle allScreens=new Rectangle(); for(GraphicsDevice d:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) allScreens=allScreens.union(d.getDefaultConfiguration().getBounds());
            BufferedImage capture=new Robot().createScreenCapture(allScreens);
            BufferedImage selected=selectArea(capture, allScreens);
            if(selected==null) { SwingUtilities.invokeLater(() -> setStatus("Captura cancelada.",false)); return; }
            SwingUtilities.invokeLater(() -> setStatus("Reconociendo el texto seleccionado...",false));
            image=Files.createTempFile("lector-ocr-", ".png"); ImageIO.write(selected,"png",image.toFile());
            String language=LANGUAGES.get(languageBox.getSelectedItem()); String text=cleanOcrText(runOcr(image,language));
            if(text.isBlank()) { SwingUtilities.invokeLater(() -> setStatus("No encontré texto legible en la pantalla.",true)); return; }
            speak(text); SwingUtilities.invokeLater(() -> setStatus("Leyendo el texto detectado.",false));
        } catch(Exception ex) { SwingUtilities.invokeLater(() -> setStatus(ex.getMessage()==null?"No se pudo leer la pantalla.":ex.getMessage(),true)); }
        finally { if(image!=null) try { Files.deleteIfExists(image); } catch(Exception ignored){} captureInProgress=false; }
        },"screen-ocr").start();
    }
    /** Muestra una captura congelada y devuelve solo el rectángulo que el usuario arrastra. */
    private BufferedImage selectArea(BufferedImage capture, Rectangle bounds) throws InterruptedException {
        CountDownLatch chosen=new CountDownLatch(1); AtomicReference<Rectangle> area=new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            JWindow overlay=new JWindow(); SelectionPanel panel=new SelectionPanel(capture, area, chosen, overlay);
            overlay.setContentPane(panel); overlay.setBounds(bounds); overlay.setAlwaysOnTop(true); overlay.setVisible(true);
            panel.requestFocusInWindow();
        });
        chosen.await(); Rectangle rect=area.get();
        if(rect==null || rect.width<4 || rect.height<4) return null;
        BufferedImage result=new BufferedImage(rect.width,rect.height,BufferedImage.TYPE_INT_RGB);
        result.getGraphics().drawImage(capture.getSubimage(rect.x,rect.y,rect.width,rect.height),0,0,null);
        return result;
    }
    private static class SelectionPanel extends JPanel {
        private final BufferedImage image; private final AtomicReference<Rectangle> result; private final CountDownLatch done; private final JWindow window;
        private Point start, end;
        SelectionPanel(BufferedImage image, AtomicReference<Rectangle> result, CountDownLatch done, JWindow window) {
            this.image=image; this.result=result; this.done=done; this.window=window; setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); setFocusable(true);
            MouseAdapter mouse=new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { start=e.getPoint(); end=start; repaint(); }
                @Override public void mouseDragged(MouseEvent e) { end=e.getPoint(); repaint(); }
                @Override public void mouseReleased(MouseEvent e) { end=e.getPoint(); result.set(selection()); finish(); }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel");
            getActionMap().put("cancel",new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ finish(); }});
        }
        private Rectangle selection() {
            if(start==null||end==null) return null;
            int x=Math.max(0,Math.min(start.x,end.x)), y=Math.max(0,Math.min(start.y,end.y));
            int right=Math.min(image.getWidth(),Math.max(start.x,end.x)), bottom=Math.min(image.getHeight(),Math.max(start.y,end.y));
            return new Rectangle(x,y,right-x,bottom-y);
        }
        private void finish() { window.dispose(); done.countDown(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D g2=(Graphics2D)g.create(); g2.drawImage(image,0,0,null);
            g2.setColor(new Color(0,0,0,120)); g2.fillRect(0,0,getWidth(),getHeight()); Rectangle r=selection();
            if(r!=null) { g2.drawImage(image,r.x,r.y,r.x+r.width,r.y+r.height,r.x,r.y,r.x+r.width,r.y+r.height,null); g2.setColor(new Color(70,180,255)); g2.setStroke(new BasicStroke(2)); g2.draw(r); }
            g2.setColor(Color.WHITE); g2.setFont(g2.getFont().deriveFont(Font.BOLD,16f)); g2.drawString("Arrastra para seleccionar el texto - Esc para cancelar",22,30); g2.dispose();
        }
    }
    private String runOcr(Path image,String language) throws Exception {
        String exe=findTesseract(); if(exe==null) throw new IllegalStateException("No encuentro Tesseract OCR. Instálalo y reinicia la app.");
        ProcessBuilder command=new ProcessBuilder(exe,image.toString(),"stdout","-l",language).redirectErrorStream(true);
        // La versión distribuida lleva el motor y sus idiomas junto a la aplicación.
        command.environment().put("TESSDATA_PREFIX",new File(exe).getParent() + File.separator + "tessdata");
        Process p=command.start(); String output=readAll(p.getInputStream());
        if(p.waitFor()!=0) throw new IllegalStateException("OCR: "+output.replaceAll("[\\r\\n]+"," ").trim()); return output;
    }
    /** Tesseract escribe diagnósticos en la misma salida en algunas instalaciones; no son texto para leer. */
    private String cleanOcrText(String output) {
        StringBuilder text=new StringBuilder();
        for(String line:output.split("\\R")) {
            String value=line.trim();
            if(value.matches("(?i)estimating resolution as \\d+") ||
                    value.matches("(?i)detected \\d+ diacritics") ||
                    value.matches("(?i)warning[.:].*resolution.*") ||
                    value.matches("(?i)osd:.*")) continue;
            if(!value.isBlank()) { if(!text.isEmpty()) text.append(' '); text.append(value); }
        }
        return text.toString().trim();
    }
    private String findTesseract() {
        // jpackage expone la ruta del lanzador. Es la fuente más fiable al usar
        // el instalador: <instalación>\\app\\tesseract\\tesseract.exe.
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) {
            File included = new File(new File(launcher).getParentFile(), "app\\tesseract\\tesseract.exe");
            if (included.isFile()) return included.getAbsolutePath();
        }
        try {
            File jar = new File(LectorPantalla.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File included = new File(jar.getParentFile(), "tesseract\\tesseract.exe");
            if (included.isFile()) return included.getAbsolutePath();
        } catch (Exception ignored) { }
        String[] candidates={"C:\\Program Files\\Tesseract-OCR\\tesseract.exe","C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe"};
        for(String s:candidates) if(new File(s).isFile()) return s;
        try { Process p=new ProcessBuilder("where.exe","tesseract.exe").start(); String found=readAll(p.getInputStream()).trim(); if(p.waitFor()==0&&!found.isBlank()) return found.split("\\R")[0].trim(); } catch(Exception ignored) { }
        return null;
    }
    private void speak(String text) throws Exception {
        if(speaking!=null&&speaking.isAlive()) speaking.destroyForcibly();
        text=naturalizePunctuation(text); String text64=Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_16LE)); String voice=String.valueOf(voiceBox.getSelectedItem());
        if(PIPER_VOICES.containsKey(voice)) { speakWithPiper(text,PIPER_VOICES.get(voice)); return; }
        if(EDGE_VOICES.containsKey(voice)) { speakWithEdge(text,EDGE_VOICES.get(voice)); return; }
        String voice64=Base64.getEncoder().encodeToString(voice.getBytes(StandardCharsets.UTF_16LE));
        String script="$t=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('"+text64+"'));$v=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('"+voice64+"'));Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;$match=$s.GetInstalledVoices()|Where-Object {$_.VoiceInfo.Name -eq $v}|Select-Object -First 1;if($match){$s.SelectVoice($v)};$s.Rate="+speechRate+";$safe=[Security.SecurityElement]::Escape($t);$safe=$safe -replace ',','<break time=''180ms''/>' -replace ';','<break time=''280ms''/>' -replace '([.!?])','$1<break time=''480ms''/>';$s.SpeakSsml(\"<speak version='1.0' xml:lang='es-ES'><prosody rate='"+speechRate+"'>$safe</prosody></speak>\")";
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)); speaking=new ProcessBuilder("powershell.exe","-NoProfile","-EncodedCommand",encoded).start();
    }
    private void speakWithPiper(String text,VoiceProfile profile) throws Exception {
        File executable=bundledFile("piper\\piper-voice.exe"); File model=bundledFile("piper\\"+profile.model());
        if(!executable.isFile()||!model.isFile()) throw new IllegalStateException("La voz neuronal no está disponible en esta versión.");
        Path source=Files.createTempFile("lector-texto-", ".txt"), audio=Files.createTempFile("lector-voz-", ".wav");
        Files.writeString(source,text,StandardCharsets.UTF_8);
        Process synthesis=new ProcessBuilder(executable.getAbsolutePath(),model.getAbsolutePath(),source.toString(),audio.toString(),String.valueOf(profile.speaker())).redirectErrorStream(true).start();
        String log=readAll(synthesis.getInputStream()); if(synthesis.waitFor()!=0) throw new IllegalStateException("Voz neuronal: "+log.trim());
        String path64=Base64.getEncoder().encodeToString(audio.toString().getBytes(StandardCharsets.UTF_16LE));
        String script="$p=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('"+path64+"'));(New-Object Media.SoundPlayer $p).PlaySync();Remove-Item -LiteralPath $p -Force";
        String command=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)); speaking=new ProcessBuilder("powershell.exe","-NoProfile","-EncodedCommand",command).start(); Files.deleteIfExists(source);
    }
    private void speakWithEdge(String text,String voice) throws Exception {
        File executable=bundledFile("piper\\edge-voice.exe"); if(!executable.isFile()) throw new IllegalStateException("La voz colombiana no está disponible en esta versión.");
        Path source=Files.createTempFile("lector-texto-", ".txt"), audio=Files.createTempFile("lector-voz-", ".mp3"); Files.writeString(source,text,StandardCharsets.UTF_8);
        Process synthesis=new ProcessBuilder(executable.getAbsolutePath(),voice,source.toString(),audio.toString()).redirectErrorStream(true).start(); String output=readAll(synthesis.getInputStream()); if(synthesis.waitFor()!=0) throw new IllegalStateException("No se pudo generar la voz en línea: "+output.trim());
        String path64=Base64.getEncoder().encodeToString(audio.toString().getBytes(StandardCharsets.UTF_16LE)); String script="$p=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('"+path64+"'));Add-Type -AssemblyName PresentationCore;$player=New-Object System.Windows.Media.MediaPlayer;$player.Open([Uri]$p);$player.Play();while(!$player.NaturalDuration.HasTimeSpan){Start-Sleep -Milliseconds 80};Start-Sleep -Milliseconds ([int]$player.NaturalDuration.TimeSpan.TotalMilliseconds+100);$player.Close();Remove-Item -LiteralPath $p -Force";
        String command=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));speaking=new ProcessBuilder("powershell.exe","-NoProfile","-EncodedCommand",command).start();Files.deleteIfExists(source);
    }
    private File bundledFile(String relative) {
        try { File jar=new File(LectorPantalla.class.getProtectionDomain().getCodeSource().getLocation().toURI()); return new File(jar.getParentFile(),relative); } catch(Exception e) { return new File(relative); }
    }
    private String naturalizePunctuation(String text) { return text.replaceAll("\\s+", " ").replaceAll("\\s*([,;:.!?])\\s*", "$1 ").replace("…", "...").trim(); }
    private void loadVoices() { new Thread(() -> { try { Process p=new ProcessBuilder("powershell.exe","-NoProfile","-Command","Add-Type -AssemblyName System.Speech;(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | Where-Object {$_.VoiceInfo.Culture.Name -like 'es-*'} | ForEach-Object {$_.VoiceInfo.Name}").start();String list=readAll(p.getInputStream());p.waitFor();SwingUtilities.invokeLater(()-> {String selected=PREFS.get("voice","España - Sara");voiceBox.removeAllItems();PIPER_VOICES.keySet().forEach(voiceBox::addItem);EDGE_VOICES.keySet().forEach(voiceBox::addItem);for(String line:list.split("\\R"))if(!line.isBlank())voiceBox.addItem(line.trim());if(selected.equals("Microsoft David Desktop")||selected.equals("Microsoft Zira Desktop")||selected.equals("Microsoft Mark Desktop")||selected.equals("Voz predeterminada de Windows"))selected="España - Sara";voiceBox.setSelectedItem(selected);}); }catch(Exception ignored){} },"voice-loader").start(); }
    private String readAll(InputStream in) throws Exception { ByteArrayOutputStream out=new ByteArrayOutputStream();in.transferTo(out);return out.toString(StandardCharsets.UTF_8); }
    private void setStatus(String message,boolean error) { if(status!=null){status.setText(message);status.setForeground(error?new Color(170,55,45):new Color(45,95,155));} }
}
