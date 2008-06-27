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

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Tape input/output emulation.
 * 
 * The cassette output worked the same as the TV speaker. Cassete input was a
 * simple zero-crossing detector that served as a relatively crude (1-bit) audio
 * digitizer. Routines in the ROM were used to encode and decode data in
 * frequency shift keying for the cassette. "1" is recorded as a 1ms cycle (
 * 0.5ms each half-cycle ) and "0" is a 0.50ms pulse ( 0.25ms each half-cycle ).
 * 
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class Tape implements Device
{

    /**
     * Represents a Chunk in a .ct2 file.
     */
    class Chunk
    {

        /* Header A */
        public static final int CA = 0;
        /* Header B */
        public static final int CB = 1;
        /* DATA */
        public static final int DA = 2;

        private int             m_type;
        private byte[]          m_data;

        public Chunk( int type )
        {
            m_type = type;
        }

        public Chunk( int type, byte[] data )
        {
            m_data = data;
            m_type = type;
        }

        public byte[] getData()
        {
            return m_data;
        }

        /**
         * Return the type of chunk.
         * 
         * @return
         */
        public int getType()
        {
            return m_type;
        }

    }

    /* Total CPU cycles in a "A" header */
    public static final int CA_CYCLES       = 500;
    // public static final int CA_CYCLES = 9472;

    /* Total CPU cycles in a "B" header */
    public static final int CB_CYCLES       = 32;
    // public static final int CB_CYCLES = 64;

    CPU6502                 m_cpu;
    Bus                     m_bus;
    int                     m_lastTicks     = 0;
    byte                    m_casout        = 0;
    int                     cpuCyclesNeeded = 0;
    int                     m_halfCycle     = 0;
    int                     m_startCpuCycle = 0;
    int[]                   m_waveBuffer;
    int                     m_waveBufferSize;
    int                     m_state         = 0;
    boolean                 m_inData        = false;
    int                     m_bitPos        = 0;
    boolean                 m_even          = true;
    boolean                 m_consume       = false;
    int                     m_byte          = 0;

    boolean                 m_sound         = true;
    boolean                 m_play          = false;

    public Tape( CPU6502 cpu, Bus bus )
    {

        m_cpu = cpu;
        m_bus = bus;
        m_waveBufferSize = 0;

    }

    /**
     * Read a .ct2 file.
     * 
     * @param tapeName
     *            the name of the ct2 file
     * @return a List of chunks.
     * @throws Exception
     */
    private List<Chunk> readTape( String tapeName ) throws Exception
    {

        LinkedList<Chunk> chunks = new LinkedList<Chunk>();
        Chunk chunk = null;

        InputStream fis = getClass().getResourceAsStream(
                "/games/" + tapeName + ".ct2" );

        try
        {

            byte[] header = new byte[ 4 ];

            readArray( fis, header );

            String magic = new String( header );

            while ( readArray( fis, header ) )
            {

                String headerType = new String( header, 0, 2 );                

                if ( "CA".equals( headerType ) )
                {
                    chunk = new Chunk( Chunk.CA );
                }

                if ( "CB".equals( headerType ) )
                {
                    chunk = new Chunk( Chunk.CB );
                }

                if ( "DA".equals( headerType ) )
                {

                    short lng = (short) ( (header[ 3 ] << 8) | (header[ 2 ] & 0xff));
                    byte[] data = new byte[ lng ];
                    readArray( fis, data );                    
                    chunk = new Chunk( Chunk.DA, data );

                }

                chunks.add( chunk );

            }
        }
        finally
        {
            if ( fis != null )
            {
                fis.close();
            }
        }

        return chunks;

    }

    private boolean readArray( InputStream is, byte[] array ) throws Exception
    {
        
        int total = 0;
        int readed = 0;
        
        while ( total < array.length )
        {

            readed = is.read( array, total, array.length - total );

            if ( readed == -1 )
            {
                return false;
            }

            total += readed;

        }

        return true;

    }

    /**
     * Create the buffer containing the cycles duration expressed as cpu cycles.
     * 
     * @param chunks
     */
    public void createBuffer( List<Chunk> chunks )
    {

        int size = calculateBufferSize( chunks );

        m_waveBuffer = new int[ size ];
        int halfWave = 0;

        Iterator i = chunks.iterator();

        while ( i.hasNext() )
        {
            Chunk chunk = (Chunk) i.next();

            switch ( chunk.getType() )
            {

                case Chunk.CA:

                    for ( int j = 0; j < CA_CYCLES; j++ )
                    {
                        m_waveBuffer[ halfWave++ ] = 502;
                        m_waveBuffer[ halfWave++ ] = 502;
                    }

                    break;

                case Chunk.CB:

                    m_waveBuffer[ halfWave++ ] = 464;
                    m_waveBuffer[ halfWave++ ] = 679;

                    for ( int j = 0; j < CB_CYCLES; j++ )
                    {
                        m_waveBuffer[ halfWave++ ] = 679;
                        m_waveBuffer[ halfWave++ ] = 679;
                    }

                    m_waveBuffer[ halfWave++ ] = 199;
                    m_waveBuffer[ halfWave++ ] = 250;

                    break;

                case Chunk.DA:

                    byte[] data = chunk.getData();

                    for ( int d = 0; d < data.length; d++ )
                    {

                        int b = data[ d ] & 0xff;

                        for ( int j = 0; j < 8; j++ )
                        {

                            int mask = 1 << (7 - j);

                            if ( (mask & b) == mask )
                            {

                                // 1
                                m_waveBuffer[ halfWave++ ] = 500;
                                m_waveBuffer[ halfWave++ ] = 500;

                            }
                            else
                            {
                                // 0
                                m_waveBuffer[ halfWave++ ] = 250;
                                m_waveBuffer[ halfWave++ ] = 250;

                            }

                        }

                    }

                    break;
            }
        }

    }

    /**
     * Calculate the cycle duration buffer size.
     * 
     * @param chunks
     * @return
     */
    private int calculateBufferSize( List chunks )
    {

        Iterator i = chunks.iterator();

        int size = 0;

        while ( i.hasNext() )
        {
            Chunk chunk = (Chunk) i.next();

            switch ( chunk.getType() )
            {

                case Chunk.CA:
                    size += (CA_CYCLES * 2);
                    break;

                case Chunk.CB:
                    size += (CB_CYCLES * 2) + 4;
                    break;

                case Chunk.DA:
                    // 16 cycles per byte.
                    size += (chunk.getData().length * 16);
                    break;
            }
        }

        System.out.println( "Wave buffer size: " + size );

        return size;

    }

    /**
     * Read CASOUT or CASIN port.
     */
    public synchronized byte read( int addr )
    {

        // if CASOUT is read then map to speaker.
        if ( addr == 0xC020 )
        {
            m_bus.read( 0xC030 );
            return 0;
        }

        // debug( addr );

        if ( !m_play || addr != 0xC010 )
        {
            return 0;
        }

        if ( m_startCpuCycle == 0 )
        {
            m_startCpuCycle = m_cpu.getClock();
            m_casout = (byte) 0x80;
            cpuCyclesNeeded = m_waveBuffer[ m_halfCycle ];
            if ( m_sound )
            {
                m_bus.read( 0xC030 );
            }
        }

        int elapsedCpuCycles = m_cpu.getClock() - m_startCpuCycle;

        if ( elapsedCpuCycles > cpuCyclesNeeded )
        {

            /* just for hear the old time tape sound */
            if ( m_sound )
            {
                m_bus.read( 0xC030 );
            }

            m_startCpuCycle = m_cpu.getClock();

            if ( m_casout == 0 )
            {
                m_casout = (byte) 0x80;
            }
            else
            {
                m_casout = 0;
            }

            m_halfCycle++;

            if ( m_halfCycle < m_waveBuffer.length )
            {
                cpuCyclesNeeded = m_waveBuffer[ m_halfCycle ];
            }
            else
            {
                m_play = false;
            }

        }

        return m_casout;

    }

    /**
     * Used for reverse engineer the tape routine.
     * 
     * @param addr
     */
    private void debug( int addr )
    {
        if ( addr == 0xC020 )
        {
            if ( m_startCpuCycle == 0 )
            {
                m_lastTicks = m_cpu.getClock();
            }

            int elapsedCpuCycles = m_cpu.getClock() - m_startCpuCycle;

            if ( elapsedCpuCycles != 0 )
            {
                m_waveBuffer[ m_halfCycle++ ] = elapsedCpuCycles;
            }

            m_startCpuCycle = m_cpu.getClock();

            if ( elapsedCpuCycles == 679 )
            {
                m_inData = false;
            }

            if ( !m_inData )
            {

                if ( elapsedCpuCycles != 250 )
                    return;

                m_inData = true;
                m_consume = true;
                System.out.println( "Data" );
                return;
            }

            if ( !m_even )
            {
                if ( elapsedCpuCycles > 400 && elapsedCpuCycles < 550 )
                {
                    m_byte = m_byte ^ (1 << (7 - m_bitPos));
                }

                m_bitPos++;

                if ( m_bitPos == 8 )
                {
                    m_bitPos = 0;
                    System.out.println( m_byte
                            + " "
                            + Integer.toHexString( m_byte ) );
                    m_byte = 0;
                }

            }

            m_even = !m_even;

            m_state = (m_state == 0) ? 1 : 0;

        }
    }

    /**
     * Write the CASOUT or CASIN port.
     */
    public void write( int addr, byte value )
    {

    }

    /**
     * Mute the sound from the tape.
     * 
     * @param on
     */
    public void setSound( boolean on )
    {
        m_sound = on;
    }

    /**
     * Insert a tape on the recorder.
     * 
     * @param name
     *            the tape name.
     * @throws Exception
     */
    public synchronized void insertTape( String name ) throws Exception
    {
        System.out.println( "Inserting tape:" + name );
        stop();
        List<Chunk> chunks = readTape( name );
        createBuffer( chunks );
    }

    /**
     * Play the tape inserted.
     * 
     */
    public synchronized void play()
    {
        m_startCpuCycle = 0;
        m_halfCycle = 0;
        m_play = true;

    }

    /**
     * Stop the playing.
     * 
     */
    public synchronized void stop()
    {
        m_play = false;
    }

}
