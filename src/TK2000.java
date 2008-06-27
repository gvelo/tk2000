/*
 *  TK2000 II Color Computer Emulator.
 *
 *  Copyright (C) 2008 Gabriel Velo <gabriel.velo@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2
 *  as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program (see the file COPYING included with this
 *  distribution); if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.InputStream;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * TK2000 Emulator.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 */
public class TK2000 implements ActionListener, ItemListener
{

    private Video     m_video;
    private Keyboard  m_keyboard;
    private Sound     m_sound;
    private RAM       m_ram;
    private ROM       m_rom;
    private Tape      m_tape;
    private CPU6502   m_cpu;
    private Container m_container;
    private Bus       m_bus;
    private BankSW    m_bankSW;

    private String[]  m_tapes = {
            "AutoEstrada",
            "BatalhaNaval",
            "Bombardeio",
            "BugAttack",
            "CeilingZero",
            "defender",
            "DesafioFatal",
            "DungBeetles",
            "Eliminator",
            "EstacaoOrbital",
            "GammaGoblins",
            "Gobber",
            "Go-Moku",
            "GranPrix",
            "Hero",
            "Karateka1",
            "Karateka2",
            "Minotauro1",
            "Minotauro2",
            "Multiflipper",
            "NightCrawler",
            "Norad",
            "OperacaoPerigo",
            "PapaTudo",
            "Poker",
            "PuloDoSapo",
            "Resgate",
            "Sabotagem",
            "SpaceAttack",
            "Suicida",
            "Swashbuckler",
            "Xadrez"         };

    private JComboBox m_tapeCombo;
    private JButton   m_playButton;
    private JButton   m_stopButton;
    private JCheckBox m_soundCheck;
    private JCheckBox m_tapeSoundCheck;
    private JButton   m_resetButton;
    private JButton   m_onOffButton;
    private JTextArea m_helpArea;
    private ImageIcon m_imageOff;
    private ImageIcon m_imageOn;
    private ImageIcon m_imageLogo;
    private boolean   m_on    = true;

    /**
     * Build the GUI in the specified container.
     * 
     * @param container
     * @throws Exception
     */
    public TK2000( Container container ) throws Exception
    {

        buildComputer();

        JPanel controls = new JPanel();
        controls.setLayout( new FlowLayout() );

        JLabel tapeLabel = new JLabel( "Tape" );
        m_tapeCombo = new JComboBox( m_tapes );
        m_tapeCombo.addActionListener( this );

        m_playButton = new JButton(
                "Play",
                createImageIcon( "/images/control_play_blue.png" ) );
        m_playButton.setActionCommand( "play" );
        m_playButton.addActionListener( this );
        m_stopButton = new JButton(
                "Stop",
                createImageIcon( "/images/control_stop.png" ) );
        m_stopButton.setActionCommand( "stop" );
        m_stopButton.addActionListener( this );
        m_soundCheck = new JCheckBox( "Sound" );
        m_soundCheck.addItemListener( this );
        m_tapeSoundCheck = new JCheckBox( "Tape Sound" );
        m_tapeSoundCheck.addItemListener( this );
        m_resetButton = new JButton(
                "Reset",
                createImageIcon( "/images/arrow_refresh.png" ) );
        m_resetButton.setActionCommand( "reset" );
        m_resetButton.addActionListener( this );

        m_imageOff = createImageIcon( "/images/disconnect.png" );
        m_imageOn = createImageIcon( "/images/connect.png" );

        m_onOffButton = new JButton( "Off", m_imageOff );
        m_onOffButton.setActionCommand( "onoff" );
        m_onOffButton.addActionListener( this );

        controls.add( tapeLabel );
        controls.add( m_tapeCombo );
        controls.add( m_playButton );
        controls.add( m_stopButton );
        controls.add( m_soundCheck );
        controls.add( m_tapeSoundCheck );
        controls.add( m_resetButton );
        controls.add( m_onOffButton );

        m_helpArea = new JTextArea();
        m_helpArea.setLineWrap( true );
        JScrollPane m_helpAreaScroll = new JScrollPane( m_helpArea );
        m_helpAreaScroll.setPreferredSize( new Dimension( 0, 150 ) );

        m_container = container;
        m_container.setLayout( new BoxLayout( m_container, BoxLayout.Y_AXIS ) );

        m_imageLogo = createImageIcon( "/images/logo.png" );
        JLabel label = new JLabel( m_imageLogo );
        label.setBorder( BorderFactory.createBevelBorder( 50 ) );
        label.setAlignmentX( Component.CENTER_ALIGNMENT );

        m_container.add( label );
        m_container.add( m_video );
        m_container.add( controls );
        m_container.add( m_helpAreaScroll );

        m_tapeCombo.setSelectedIndex( 0 );
        m_soundCheck.setSelected( true );
        m_tapeSoundCheck.setSelected( true );

        if ( !m_sound.isAvailable() )
        {
            String errorTip = "<html><strong>Can't get sound.</strong><br>"
                    + m_sound.getException().toString()
                    + "</html>";

            m_soundCheck.setEnabled( false );
            m_tapeSoundCheck.setEnabled( false );

            m_soundCheck.setToolTipText( errorTip );
            m_tapeSoundCheck.setToolTipText( errorTip );

        }
    }

    private ImageIcon createImageIcon( String path )
    {
        URL url = getClass().getResource( path );
        return new ImageIcon( url );
    }

    /**
     * @param args
     */
    public static void main( String[] args ) throws Exception
    {

        UIManager.setLookAndFeel( "javax.swing.plaf.metal.MetalLookAndFeel" );
        UIManager.put( "swing.boldMetal", Boolean.FALSE );

        JFrame frame = new JFrame();
        frame.setTitle( "TK2000 II COLOR COMPUTER EMULATOR" );

        TK2000 tk2000 = new TK2000( frame.getContentPane() );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        frame.setVisible( true );
        frame.pack();
        tk2000.run();

    }

    /**
     * Wire the computer.
     * 
     * @throws Exception
     */
    public void buildComputer() throws Exception
    {

        m_bus = new Bus();
        m_cpu = new CPU6502( m_bus );
        m_ram = new RAM();
        m_rom = new ROM();
        m_keyboard = new Keyboard( m_cpu );
        m_tape = new Tape( m_cpu, m_bus );
        m_video = new Video( m_bus );
        m_video.addKeyListener( m_keyboard );
        m_video.setScale( 1.0f );
        m_sound = new Sound( m_cpu );
        m_bankSW = new BankSW( m_bus, m_ram, m_rom );

        // Memory Map

        m_bus.setDevice( 0x0000, 0xBFFF, m_ram );
        m_bus.setDevice( 0xC000, 0xC01F, m_keyboard );
        m_bus.addDevice( 0xC010, m_tape );
        m_bus.setDevice( 0xC020, 0xC02F, m_tape );
        m_bus.setDevice( 0xC030, 0xC03F, m_sound );
        m_bus.setDevice( 0xC050, 0xC051, m_video );
        m_bus.setDevice( 0xC052, 0xC053, m_tape );
        m_bus.setDevice( 0xC054, 0xC055, m_video );
        m_bus.setDevice( 0xC056, 0xC057, m_tape );
        m_bus.setDevice( 0xC05A, 0xC05B, m_bankSW );
        m_bus.setDevice( 0xC05E, 0xC05F, m_keyboard );
        m_bus.setDevice( 0xC070, 0xC071, m_tape );
        m_bus.setDevice( 0xC080, 0xC08B, m_bankSW );
        m_bus.setDevice( 0xC100, 0xFFFF, m_rom );

        paintVideoMemory();

    }

    /**
     * Draw the white strips in the video memory.
     * 
     */
    private void paintVideoMemory()
    {

        for ( int i = 0; i < 0x1000; i++ )
        {
            m_bus.write( i + 0x2000, (byte) 0xFF );
            m_bus.write( i + 0xA000, (byte) 0xFF );
        }

    }

    /**
     * Reset the computer.
     * 
     */
    public void reset()
    {
        // Select ROM banks.
        m_bus.read( BankSW.BANK_ROM );
        // Select video base.
        m_bus.read( 0xC054 );
        m_video.setColorMode( 0 );
        m_cpu.assertReset();
    }

    /**
     * Turn on the computer.
     * 
     */
    public void run()
    {
        m_video.start();
        m_cpu.assertReset();
        m_cpu.start();

    }

    /**
     * Turn off the computer.
     * 
     */
    public void stop()
    {
        m_video.stop();
        m_cpu.stop();
    }

    public void actionPerformed( ActionEvent event )
    {

        m_video.requestFocus();

        if ( "reset".equals( event.getActionCommand() ) )
        {
            reset();
        }

        if ( "comboBoxChanged".equals( event.getActionCommand() ) )
        {

            m_tape.stop();
            m_playButton.setEnabled( true );
            try
            {
                String name = (String) m_tapeCombo.getSelectedItem();
                m_tape.insertTape( name );
                String help = getTapeHelp( name );
                m_helpArea.setText( help );
                m_helpArea.moveCaretPosition( 0 );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        m_container,
                        e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE );
            }
        }

        if ( "play".equals( event.getActionCommand() ) )
        {
            m_tape.play();
            m_playButton.setEnabled( false );
        }

        if ( "stop".equals( event.getActionCommand() ) )
        {
            m_tape.stop();
            m_playButton.setEnabled( true );
        }

        if ( "onoff".equals( event.getActionCommand() ) )
        {

            m_on = !m_on;

            if ( m_on )
            {
                m_onOffButton.setText( "Off" );
                m_onOffButton.setIcon( m_imageOff );
                paintVideoMemory();
                reset();
                m_cpu.start();
            }
            else

            {
                m_cpu.stop();
                m_ram.clear();
                m_onOffButton.setText( "On" );
                m_onOffButton.setIcon( m_imageOn );
            }

        }

    }

    public void itemStateChanged( ItemEvent event )
    {

        m_video.requestFocus();

        if ( event.getSource() == m_tapeSoundCheck )
        {

            if ( event.getStateChange() == ItemEvent.SELECTED )
            {
                m_tape.setSound( true );
            }
            else
            {
                m_tape.setSound( false );

            }
        }

        if ( event.getSource() == m_soundCheck )
        {

            if ( event.getStateChange() == ItemEvent.SELECTED )
            {
                m_sound.setEnable( true );
            }
            else
            {
                m_sound.setEnable( false );
            }
        }

    }

    private String getTapeHelp( String name )
    {

        try
        {
            InputStream fis = getClass().getResourceAsStream(
                    "/games/" + name + ".txt" );

            if ( fis == null )
            {
                return "";
            }

            byte[] strBytes = new byte[ fis.available() ];

            fis.read( strBytes );

            return new String( strBytes, "ISO-8859-1" );

        }
        catch ( Exception e )
        {
            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    m_container,
                    e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE );

            return "";
        }

    }

}
