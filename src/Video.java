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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.swing.JPanel;

/**
 * Emulate the video modulator.
 * 
 * The TK2000 video raster is the same that the Apple II.
 * 
 * The resolution of an Apple II Graphics screen was 280 pixels x 192 rasters
 * and allowed up to 6 colors to be displayed simultaneously. However, the
 * hardware was designed to interpret the colors based on "rules" of pixel
 * arrangement in a raster line, and colors would "bleed" (artifact) into each
 * other if the pixels were incompatibly arranged. This was a memory saving
 * technique that allowed a 6 color image to be displayed in only 8K of video
 * memory.
 * 
 * Each scanline is 40 bytes long. There are 3 parent interleaves of 64
 * scanlines each. Starting at the beginning of the image file, scanlines are
 * arranged in 128 byte blocks beginning with scanlines 0, 64, and 128 in the
 * first block (see table below). 8 bytes at the end of each block are unused by
 * the image.
 * 
 * Each parent interleaf has 8 child interleaves of 8 scanlines each, with a
 * spacing of 1024 bytes (1K) between sequential scanlines in each of the 3
 * parent interleaves.
 * 
 * Starting at the beginning of the file and respective of the Apple II's
 * interleaved display, the first 8 of the 128 byte blocks run as shown below.
 * This also represents the first of 8 scanlines in each of the 24 child
 * interleaves, and is effectively a block group. 8 of these block groups are
 * stored in the file, with the scanlines in the table below being incremented
 * by 1 for each subsequent block group sequence of 1024 bytes, totalling 8192
 * bytes (8K) of graphics image data.
 * 
 * The colors in an Apple II image are Black, Green, Violet, Orange, Blue, and
 * White. Pixel color is represented by 2 bits. Although the nominal resolution
 * is 280 x 192, the effective resolution considering colors is really only 140
 * x 192.
 * 
 * In a 40 byte scanline pixels are stored in an bit array of 280 pixel
 * positions referred to as "columns". The highest bit of each byte is used as a
 * "palette" bit and is not considered a column. The lowest bit represents the
 * first column in the byte. So a scan line beginning with $03 would have the
 * first two pixel positions set as white (with the next 5 pixels black).
 * 
 * If the palette bit in a byte is set, the 4 colors available for the pixels in
 * that byte are black, blue, orange, and white.
 * 
 * If the palette bit is not set, the 4 colors available for the pixels in that
 * byte are black, violet, green, and white
 * 
 * Ignoring the 40 palette bits in in a 40 byte scanline, there are 280 columns
 * (bits, pixel positions) but only 140 available pixels. So considering each
 * pixel as being a column pair, with an even and odd column, from left to right
 * starting at column 0, if even is set and odd is not set, the pixel is blue or
 * violet. If even is not set and odd is set, the pixel is orange or green. Any
 * two adjacent columns that are set will be white. This means that white pixels
 * (and black pixels) can be positioned at more places than the other colors,
 * but does not alter the fact that the effective resolution is really only 140
 * x 192.
 * 
 * 
 * taken from
 * http://en.wikipedia.org/wiki/BSAVE_(graphics_image_format)#Specifications_Apple_II
 * 
 * 
 * Adapted from code by Marc S. Ressl(ressl@lonetree.com).
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class Video extends JPanel implements Device, MouseListener,
        FocusListener
{

    /**
     * Video refresh thread.
     */
    class VideoThread extends Thread
    {

        boolean stop = false;
        Object  lock = new Object();

        public VideoThread()
        {
            super( "Video Thread" );
            System.out.println( "Creating new Video thread." );
        }

        public void run()
        {
            while ( !stop )
            {
                refreshDisplay();
                synchronized ( lock )
                {
                    try
                    {
                        lock.wait( 100 );
                        // System.out.println("Refreshing video");
                    }
                    catch ( InterruptedException e )
                    {
                        continue;
                    }
                }

            }

            System.out.println( "Video Thread stopped" );

        }

        public void stopExecution()
        {
            synchronized ( lock )
            {
                stop = true;
                lock.notifyAll();
            }
        }

    }

    VideoThread                m_thread               = null;

    Bus                        m_bus;

    // Configuration variables
    public static final int    COLORMODE_BW           = 0;
    public static final int    COLORMODE_COLOR        = 1;

    private int                m_colorMode;
    private int                m_baseAddressHires     = 0x2000;

    private boolean            m_isPrecalcRequested   = true;

    // Display
    private static final int   DISPLAY_CHAR_SIZE_X    = 7;
    private static final int   DISPLAY_CHAR_SIZE_Y    = 8;
    private static final int   DISPLAY_CHAR_COUNT_X   = 80;
    private static final int   DISPLAY_CHAR_COUNT_Y   = 24;

    public static final int    DISPLAY_SIZE_X         = DISPLAY_CHAR_COUNT_X
                                                              * DISPLAY_CHAR_SIZE_X;
    public static final int    DISPLAY_SIZE_Y         = DISPLAY_CHAR_COUNT_Y
                                                              * DISPLAY_CHAR_SIZE_Y;

    // Display composition
    private BufferedImage      m_displayImage;
    private int[]              m_displayImageBuffer;

    // Display scale
    private float              m_displayScale;
    private int                m_displayScaledSizeX;
    private int                m_displayScaledSizeY;

    // Display palette
    private int[]              m_displayPalette;

    private static final int[] m_displayPaletteBW     = {
            0x000000,
            0x0e470e,
            0x041204,
            0x166e16,
            0x0f4a0f,
            0x115411,
            0x0c3b0c,
            0x1f9e1f,
            0x125c12,
            0x1b8a1b,
            0x22ab22,
            0x24b524,
            0x1A871a,
            0x2de32d,
            0x25bd25,
            0xffffff                                 };

    private static final int[] m_displayPaletteColor  = {
            0x000000,
            0xdd0033,
            0x000099,
            0xdd22dd,
            0x007722,
            0x555555,
            0x2222ff,
            0x66aaff,
            0x885500,
            0xff6600,
            0xaaaaaa,
            0xff9988,
            0x11dd00,
            0xffff00,
            0x44ff99,
            0xffffff                                 };

    private static final int[] m_textLineAddress      = {
            0x0000,
            0x0080,
            0x0100,
            0x0180,
            0x0200,
            0x0280,
            0x0300,
            0x0380,
            0x0028,
            0x00a8,
            0x0128,
            0x01a8,
            0x0228,
            0x02a8,
            0x0328,
            0x03a8,
            0x0050,
            0x00d0,
            0x0150,
            0x01d0,
            0x0250,
            0x02d0,
            0x0350,
            0x03d0,                                  };

    // Hires stuff
    private int                m_hiresEvenOddToWord[] = new int[ 0x200 ];
    private int                m_hiresWord[]          = new int[ 8 ];
    private int                m_hiresWordNext[]      = new int[ 8 ];
    private int                m_hiresLookup[]        = new int[ 0x100 ];           // Bits:
    // [NNccccPP]
    // -
    // Next,
    // current,
    // Previous
    // bits
    private static final int   m_hiresLookupColor[]   = {
                                                      // Bits: [PPNNcccc] -
            // Previous, Next, current
            // bits => 4 pixel @ 4 bit
            // color output
            // Color-bleeding algorithm
            0x0000,
            0x0111,
            0x2222,
            0x2333,
            0x4440,
            0x4551,
            0x6662,
            0x6773,
            0x8800,
            0x8911,
            0xaa22,
            0xab33,
            0xcc40,
            0xcd51,
            0xee62,
            0xef73, // 00cccc00
            0x1000,
            0x1111,
            0x3222,
            0x3333,
            0x5440,
            0x5551,
            0x7662,
            0x7773,
            0x9800,
            0x9911,
            0xba22,
            0xbb33,
            0xdc40,
            0xdd51,
            0xfe62,
            0xff73, // 01cccc00
            0x0000,
            0x0111,
            0x2222,
            0x2333,
            0x4440,
            0x4551,
            0x6662,
            0x6773,
            0x8800,
            0x8911,
            0xaa22,
            0xab33,
            0xcc40,
            0xcd51,
            0xee62,
            0xef73, // 10cccc00
            0x1000,
            0x1111,
            0x3222,
            0x3333,
            0x5440,
            0x5551,
            0x7662,
            0x7773,
            0x9800,
            0x9911,
            0xba22,
            0xbb33,
            0xdc40,
            0xdd51,
            0xfe62,
            0xff73, // 11cccc00
            0x0004,
            0x0115,
            0x2226,
            0x2337,
            0x4444,
            0x4555,
            0x6666,
            0x6777,
            0x8804,
            0x8915,
            0xaa26,
            0xab37,
            0xcc44,
            0xcd55,
            0xee66,
            0xef77, // 00cccc01
            0x1004,
            0x1115,
            0x3226,
            0x3337,
            0x5444,
            0x5555,
            0x7666,
            0x7777,
            0x9804,
            0x9915,
            0xba26,
            0xbb37,
            0xdc44,
            0xdd55,
            0xfe66,
            0xff77, // 01cccc01
            0x0004,
            0x0115,
            0x2226,
            0x2337,
            0x4444,
            0x4555,
            0x6666,
            0x6777,
            0x8804,
            0x8915,
            0xaa26,
            0xab37,
            0xcc44,
            0xcd55,
            0xee66,
            0xef77, // 10cccc01
            0x1004,
            0x1115,
            0x3226,
            0x3337,
            0x5444,
            0x5555,
            0x7666,
            0x7777,
            0x9804,
            0x9915,
            0xba26,
            0xbb37,
            0xdc44,
            0xdd55,
            0xfe66,
            0xff77, // 11cccc01
            0x0088,
            0x0199,
            0x22aa,
            0x23bb,
            0x44c8,
            0x45d9,
            0x66ea,
            0x67fb,
            0x8888,
            0x8999,
            0xaaaa,
            0xabbb,
            0xccc8,
            0xcdd9,
            0xeeea,
            0xeffb, // 00cccc10
            0x1088,
            0x1199,
            0x32aa,
            0x33bb,
            0x54c8,
            0x55d9,
            0x76ea,
            0x77fb,
            0x9888,
            0x9999,
            0xbaaa,
            0xbbbb,
            0xdcc8,
            0xddd9,
            0xfeea,
            0xfffb, // 01cccc10
            0x0088,
            0x0199,
            0x22aa,
            0x23bb,
            0x44c8,
            0x45d9,
            0x66ea,
            0x67fb,
            0x8888,
            0x8999,
            0xaaaa,
            0xabbb,
            0xccc8,
            0xcdd9,
            0xeeea,
            0xeffb, // 10cccc10
            0x1088,
            0x1199,
            0x32aa,
            0x33bb,
            0x54c8,
            0x55d9,
            0x76ea,
            0x77fb,
            0x9888,
            0x9999,
            0xbaaa,
            0xbbbb,
            0xdcc8,
            0xddd9,
            0xfeea,
            0xfffb, // 11cccc10
            0x008c,
            0x019d,
            0x22ae,
            0x23bf,
            0x44cc,
            0x45dd,
            0x66ee,
            0x67ff,
            0x888c,
            0x899d,
            0xaaae,
            0xabbf,
            0xcccc,
            0xcddd,
            0xeeee,
            0xefff, // 00cccc11
            0x108c,
            0x119d,
            0x32ae,
            0x33bf,
            0x54cc,
            0x55dd,
            0x76ee,
            0x77ff,
            0x988c,
            0x999d,
            0xbaae,
            0xbbbf,
            0xdccc,
            0xdddd,
            0xfeee,
            0xffff, // 01cccc11
            0x008c,
            0x019d,
            0x22ae,
            0x23bf,
            0x44cc,
            0x45dd,
            0x66ee,
            0x67ff,
            0x888c,
            0x899d,
            0xaaae,
            0xabbf,
            0xcccc,
            0xcddd,
            0xeeee,
            0xefff, // 10cccc11
            0x108c,
            0x119d,
            0x32ae,
            0x33bf,
            0x54cc,
            0x55dd,
            0x76ee,
            0x77ff,
            0x988c,
            0x999d,
            0xbaae,
            0xbbbf,
            0xdccc,
            0xdddd,
            0xfeee,
            0xffff, // 11cccc11

            // First table
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 00cccc00
            0x0000,
            0x1111,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 01cccc00
            0x0000,
            0x0001,
            0x2222,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 10cccc00
            0x0000,
            0x0001,
            0x0020,
            0x3333,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0xf000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xff00,
            0xdd0d,
            0xfff0,
            0xffff, // 11cccc00
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x4444,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 00cccc01
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x5555,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 01cccc01
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x6666,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 10cccc01
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x7777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 11cccc01
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0fff,
            0x8888,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 00cccc10
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9999,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 01cccc10
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xaaaa,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 10cccc10
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xbbbb,
            0xcc00,
            0xdd0d,
            0xeee0,
            0xffff, // 11cccc10
            0x0000,
            0x000f,
            0x0020,
            0x00ff,
            0x0400,
            0x0505,
            0x0660,
            0x0fff,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcccc,
            0xdd0d,
            0xeee0,
            0xffff, // 00cccc11
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdddd,
            0xeee0,
            0xffff, // 01cccc11
            0x0000,
            0x0001,
            0x0020,
            0x0033,
            0x0400,
            0x0505,
            0x0660,
            0x0777,
            0x8000,
            0x9009,
            0xa0a0,
            0xb0bb,
            0xcc00,
            0xdd0d,
            0xeeee,
            0xffff, // 10cccc11
            0x0000,
            0x000f,
            0x00f0,
            0x00ff,
            0x0f00,
            0x0f0f,
            0x0ff0,
            0x0fff,
            0xf000,
            0xf00f,
            0xf0f0,
            0xf0ff,
            0xff00,
            0xff0f,
            0xfff0,
            0xffff, // 11cccc11

                                                      };

    /**
     * Video emulator constructor.
     * 
     * @param bus the system bus.
     */
    public Video( Bus bus )
    {

        this.m_bus = bus;

        // Create display image
        m_displayImage = new BufferedImage(
                DISPLAY_SIZE_X,
                DISPLAY_SIZE_Y * 2,
                BufferedImage.TYPE_INT_RGB );
        m_displayImageBuffer = ((DataBufferInt) m_displayImage.getRaster()
                .getDataBuffer()).getData();
        
        precalcHiresEvenEddToWord();

        // Set parameters
        setScale( 1.0f );
        setColorMode( COLORMODE_BW );

        Dimension panelDimmension = new Dimension(
                DISPLAY_SIZE_X,
                DISPLAY_SIZE_Y * 2 );
        setPreferredSize( panelDimmension );
        setMaximumSize( panelDimmension );

        addMouseListener( this );
        addFocusListener( this );
        setFocusable( true );
        setAlignmentX( Component.CENTER_ALIGNMENT );

    }

    /**
     * Set scale
     */
    public void setScale( float value )
    {
        if ( value <= 0.0f )
            return;

        m_displayScale = value;
        m_isPrecalcRequested = true;
    }

    /**
     * Get scale
     */
    public float getScale()
    {
        return m_displayScale;
    }

    /**
     * Set color mode
     */
    public void setColorMode( int value )
    {
        m_colorMode = value;
        m_isPrecalcRequested = true;
    }

    /**
     * Get color mode
     */
    public int getColorMode()
    {
        return m_colorMode;
    }

    /**
     * Paint function
     * 
     * @param g
     *            Graphics object
     */
    public void paint( Graphics g )
    {

        if ( m_displayImage != null )
            g.drawImage( m_displayImage, 0, // Destination x
                    0, // Destination y
                    m_displayScaledSizeX, // Destination x1
                    (m_displayScaledSizeY * 2), // Destination y1
                    0, // source x
                    0, // source y
                    DISPLAY_SIZE_X, // source x1
                    DISPLAY_SIZE_Y * 2, // source y1
                    null );

    }

    /**
     * Display refresh
     */
    private void refreshDisplay()
    {

        // Precalculation
        if ( m_isPrecalcRequested )
        {
            m_isPrecalcRequested = false;
            precalcDisplay();
        }

        renderHires( m_baseAddressHires );
        repaint();

    }

    /**
     * Display precalculation
     */
    private void precalcDisplay()
    {
        // Display scaled size

        m_displayScaledSizeX = (int) (DISPLAY_SIZE_X * m_displayScale);
        m_displayScaledSizeY = (int) (DISPLAY_SIZE_Y * m_displayScale);

        // Prepare display palette
        setDisplayPalette();

        // Prepare hires graphics
        precalcHiresLookup();
    }

    /**
     * Precalculate hires even odd to word
     */
    private void precalcHiresEvenEddToWord()
    {
        for ( int value = 0; value < 0x200; value++ )
        {
            m_hiresEvenOddToWord[ value ] = ( (value & 0x01) << 0)
                    | ( (value & 0x01) << 1)
                    | ( (value & 0x02) << 1)
                    | ( (value & 0x02) << 2)
                    | ( (value & 0x04) << 2)
                    | ( (value & 0x04) << 3)
                    | ( (value & 0x08) << 3)
                    | ( (value & 0x08) << 4)
                    | ( (value & 0x10) << 4)
                    | ( (value & 0x10) << 5)
                    | ( (value & 0x20) << 5)
                    | ( (value & 0x20) << 6)
                    | ( (value & 0x40) << 6)
                    | ( (value & 0x40) << 7);

            if ( (value & 0x80) != 0 )
            {
                m_hiresEvenOddToWord[ value ] <<= 1;
                m_hiresEvenOddToWord[ value ] |= ( (value & 0x100) >> 8);
            }
        }
    }

    /**
     * Precalculate hires
     */
    private void precalcHiresLookup()
    {
        if ( m_colorMode == COLORMODE_COLOR )
        {
            for ( int value = 0; value < 0x100; value++ )
                m_hiresLookup[ value ] = m_hiresLookupColor[ ( (value << 6) & 0xff)
                        | (value >> 2) ];
        }
        else
        {
            for ( int value = 0; value < 0x100; value++ )
                m_hiresLookup[ value ] = ( ( (value & 0x04) != 0) ? 0x000f : 0)
                        | ( ( (value & 0x08) != 0) ? 0x00f0 : 0)
                        | ( ( (value & 0x10) != 0) ? 0x0f00 : 0)
                        | ( ( (value & 0x20) != 0) ? 0xf000 : 0);
        }
    }

    /**
     * Render hires canvas
     */
    private final void renderHiresWord( int destOffset, int hiresNibble )
    {
        m_displayImageBuffer[ destOffset + 0 ] = m_displayPalette[ (hiresNibble >> 0) & 0xf ];
        m_displayImageBuffer[ destOffset + 1 ] = m_displayPalette[ (hiresNibble >> 4) & 0xf ];
        m_displayImageBuffer[ destOffset + 2 ] = m_displayPalette[ (hiresNibble >> 8) & 0xf ];
        m_displayImageBuffer[ destOffset + 3 ] = m_displayPalette[ (hiresNibble >> 12) & 0xf ];
    }

    private final void renderHiresScanLine( int destOffset, int hiresWord )
    {
        renderHiresWord(
                destOffset + 0,
                m_hiresLookup[ (hiresWord >> 0) & 0xff ] );
        renderHiresWord(
                destOffset + 4,
                m_hiresLookup[ (hiresWord >> 4) & 0xff ] );
        renderHiresWord(
                destOffset + 8,
                m_hiresLookup[ (hiresWord >> 8) & 0xff ] );
        renderHiresWord(
                destOffset + 12,
                m_hiresLookup[ (hiresWord >> 12) & 0xff ] );
        renderHiresWord(
                destOffset + 16,
                m_hiresLookup[ (hiresWord >> 16) & 0xff ] );
        renderHiresWord(
                destOffset + 20,
                m_hiresLookup[ (hiresWord >> 20) & 0xff ] );
        renderHiresWord(
                destOffset + 24,
                m_hiresLookup[ (hiresWord >> 24) & 0xff ] );
    }

    private final void renderHiresBlock( int destOffset )
    {
        renderHiresScanLine( destOffset, m_hiresWord[ 0 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 1 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 2 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 3 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 4 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 5 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 6 ] );
        destOffset += DISPLAY_SIZE_X * 2;
        renderHiresScanLine( destOffset, m_hiresWord[ 7 ] );
    }

    private final void resetHiresWords()
    {
        m_hiresWord[ 0 ] = 0;
        m_hiresWord[ 1 ] = 0;
        m_hiresWord[ 2 ] = 0;
        m_hiresWord[ 3 ] = 0;
        m_hiresWord[ 4 ] = 0;
        m_hiresWord[ 5 ] = 0;
        m_hiresWord[ 6 ] = 0;
        m_hiresWord[ 7 ] = 0;
    }

    private final void bufferHiresWords()
    {
        m_hiresWord[ 0 ] = m_hiresWordNext[ 0 ];
        m_hiresWord[ 1 ] = m_hiresWordNext[ 1 ];
        m_hiresWord[ 2 ] = m_hiresWordNext[ 2 ];
        m_hiresWord[ 3 ] = m_hiresWordNext[ 3 ];
        m_hiresWord[ 4 ] = m_hiresWordNext[ 4 ];
        m_hiresWord[ 5 ] = m_hiresWordNext[ 5 ];
        m_hiresWord[ 6 ] = m_hiresWordNext[ 6 ];
        m_hiresWord[ 7 ] = m_hiresWordNext[ 7 ];
    }

    private final void calcNextHiresWord(
            int hiresWordIndex,
            int byteEven,
            int byteOdd )
    {
        m_hiresWordNext[ hiresWordIndex ] = m_hiresWord[ hiresWordIndex ] >> 28;
        m_hiresWordNext[ hiresWordIndex ] |= m_hiresEvenOddToWord[ (byteEven & 0xff)
                | ( (m_hiresWordNext[ hiresWordIndex ] & 0x2) << 7) ] << 2;
        m_hiresWordNext[ hiresWordIndex ] |= m_hiresEvenOddToWord[ (byteOdd & 0xff)
                | ( (m_hiresWordNext[ hiresWordIndex ] & 0x8000) >> 7) ] << 16;
        m_hiresWord[ hiresWordIndex ] |= (m_hiresWordNext[ hiresWordIndex ] << 28);
    }

    private final void calcNextHiresWords( int address )
    {
        calcNextHiresWord( 0, m_bus.read( address + 0x00000 ), m_bus
                .read( address + 0x00001 ) );
        calcNextHiresWord( 1, m_bus.read( address + 0x00400 ), m_bus
                .read( address + 0x00401 ) );
        calcNextHiresWord( 2, m_bus.read( address + 0x00800 ), m_bus
                .read( address + 0x00801 ) );
        calcNextHiresWord( 3, m_bus.read( address + 0x00c00 ), m_bus
                .read( address + 0x00c01 ) );
        calcNextHiresWord( 4, m_bus.read( address + 0x01000 ), m_bus
                .read( address + 0x01001 ) );
        calcNextHiresWord( 5, m_bus.read( address + 0x01400 ), m_bus
                .read( address + 0x01401 ) );
        calcNextHiresWord( 6, m_bus.read( address + 0x01800 ), m_bus
                .read( address + 0x01801 ) );
        calcNextHiresWord( 7, m_bus.read( address + 0x01c00 ), m_bus
                .read( address + 0x01c01 ) );
    }

    private void renderHires( int baseAddress )
    {
        int screenCharY, screenCharYEnd = 24;
        int displayOffset;
        int address, addressEnd, addressStart;

        displayOffset = 0;
        for ( screenCharY = 0; screenCharY < screenCharYEnd; screenCharY++ )
        {
            addressStart = baseAddress + m_textLineAddress[ screenCharY ];

            addressEnd = addressStart + 40;

            resetHiresWords();
            calcNextHiresWords( addressStart );
            for ( address = (addressStart + 2); address < addressEnd; address += 2 )
            {
                bufferHiresWords();
                calcNextHiresWords( address );
                renderHiresBlock( displayOffset );
                displayOffset += DISPLAY_CHAR_SIZE_X * 4;
            }
            bufferHiresWords();
            renderHiresBlock( displayOffset );

            displayOffset += DISPLAY_CHAR_SIZE_X * 4;
            displayOffset += (DISPLAY_CHAR_SIZE_Y - 1)
                    * (DISPLAY_SIZE_X * 2)
                    + DISPLAY_SIZE_X;

        }
    }

    /**
     * Set palette
     */
    private void setDisplayPalette()
    {
        m_displayPalette = (m_colorMode == COLORMODE_COLOR)
                ? m_displayPaletteColor
                : m_displayPaletteBW;
    }

    public byte read( int addr )
    {

        switch ( addr )
        {
            case 0xC050:
                setColorMode( COLORMODE_COLOR );
                break;
            case 0xC051:
                setColorMode( COLORMODE_BW );
                break;
            case 0xC054:
                // MA
                m_baseAddressHires = 0x2000;
                break;
            case 0xC055:
                // MP
                m_baseAddressHires = 0xA000;
                break;

            default:
                System.out.print( "Unknow video softswitch" );
                break;
        }

        return (byte) 0xFF;
    }

    public void write( int addr, byte value )
    {
        read( addr );
    }

    public synchronized void stop()
    {
        if ( m_thread == null )
        {
            return;
        }

        m_thread.stopExecution();
        m_thread = null;
    }

    public void start()
    {

        if ( m_thread != null )
        {
            return;
        }

        m_thread = new VideoThread();

        m_thread.start();

    }

    public void mouseClicked( MouseEvent arg0 )
    {
        requestFocusInWindow();
    }

    public void mouseEntered( MouseEvent arg0 )
    {

    }

    public void mouseExited( MouseEvent arg0 )
    {

    }

    public void mousePressed( MouseEvent arg0 )
    {

    }

    public void mouseReleased( MouseEvent arg0 )
    {

    }

    public void focusGained( FocusEvent arg0 )
    {

    }

    public void focusLost( FocusEvent arg0 )
    {

    }

}
