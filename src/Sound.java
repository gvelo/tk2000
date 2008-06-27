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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Sound emulation.
 * 
 * The sound circuitry is the same that the Apple II. Rather than having a
 * dedicated sound-synthesis chip, the Apple II had a toggle circuit that could
 * only emit a click through the TV speaker or a line out jack ( for tape
 * recording ) . When 0xC030 is read the line is toggled generating a square
 * wave.
 * 
 * TODO: Implement buffering.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class Sound implements Device
{

    private CPU6502        m_cpu;
    private long           m_lastCycle = 0;
    private boolean        m_soundOn   = false;
    private byte[]         m_soundBuffer;
    private SourceDataLine m_sourceLine;
    private float          m_ticklong  = 1 / 1000000f;
    private Exception      m_exception;
    private boolean        m_enable;

    public Sound( CPU6502 cpu )
    {

        m_cpu = cpu;
        m_soundBuffer = new byte[ 16000 ];

        m_enable = true;

        try
        {
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    8,
                    1,
                    1,
                    16000,
                    true );
            DataLine.Info sourceInfo = new DataLine.Info(
                    SourceDataLine.class,
                    format,
                    32000 );
            m_sourceLine = (SourceDataLine) AudioSystem.getLine( sourceInfo );
            m_sourceLine.open( format, 32000 );
            m_sourceLine.start();
        }
        catch ( Exception e )
        {
            m_exception = e;
            m_enable = false;
        }

    }

    public byte read( int addr )
    {
        doSound();
        return (byte) 0xFF;
    }

    public void write( int addr, byte value )
    {
        doSound();
    }

    /**
     * Perform the line toggle and calculate the time between events.
     * 
     */
    private void doSound()
    {

        if ( !isAvailable() || !m_enable )
        {
            return;
        }

        if ( m_lastCycle == 0 )
        {
            m_lastCycle = m_cpu.getClock();
            m_soundOn = true;
            return;
        }

        int duration = (int) (m_cpu.getClock() - m_lastCycle);

        m_lastCycle = m_cpu.getClock();

        byte amplitude = (byte) ( (m_soundOn) ? 120 : 0);

        int samples = (int) ( (duration * m_ticklong) * 16000);

        if ( samples > 16000 )
        {
            samples = 0;
        }

        for ( int i = 0; i < samples; i++ )
        {
            m_soundBuffer[ i ] = amplitude;
        }

        m_sourceLine.write( m_soundBuffer, 0, samples );

        /* toggle the output */
        m_soundOn = !m_soundOn;

    }

    /**
     * Return true if a sound line is available.
     * 
     * @return
     */
    public boolean isAvailable()
    {
        return m_exception == null;
    }

    /**
     * Return the exception occurred trying to get a sound line.
     * 
     * @return
     */
    public Exception getException()
    {
        return m_exception;
    }

    /**
     * Enable or mute the sound.
     * 
     * @param enable
     */
    public void setEnable( boolean enable )
    {
        m_enable = enable;
    }

}
