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

/**
 * The 6502 Emulator.
 *  http://en.wikipedia.org/wiki/MOS_Technology_6502.
 * 
 * Adapted from code by Marc S. Ressl(ressl@lonetree.com).
 * Adapted from code by Doug Kwan.
 * Adapted from code by Randy Frank randy@tessa.iaf.uiowa.edu.
 * Adapted from code (C) 1989 Ben Koning [556498717 408/738-1763 ben@apple.com]
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class CPU6502
{

    ExecutionThread         m_executionThread;

    /** the system bus */
    private Bus             m_bus;

    /**
     * CPU Registers
     */
    public int              m_A, m_X, m_Y, m_P, m_S, m_PC;

    /**
     * CPU Clock
     */
    protected int           m_clock;

    /**
     * CPU Flags
     */
    public static final int FLAG_C              = (1 << 0);
    public static final int FLAG_Z              = (1 << 1);
    public static final int FLAG_I              = (1 << 2);
    public static final int FLAG_D              = (1 << 3);
    public static final int FLAG_B              = (1 << 4);
    public static final int FLAG_V              = (1 << 6);
    public static final int FLAG_N              = (1 << 7);
    /*
     * owing to a bug in 6502, the bit 5 must be always 1; otherwise, programs
     * like DOS 3.3 will break down see instructions in $9FF4-$9FF5 of DOS 3.3
     */

    /**
     * CPU Signals
     */
    private int             m_exceptionRegister = 0;

    public static final int SIG_6502_RESET      = (1 << 0);
    public static final int SIG_6502_NMI        = (1 << 1);
    public static final int SIG_6502_IRQ        = (1 << 2);

    /**
     * CPU IRQ State
     */
    private int             m_pendingIRQ;

    /**
     * Emulator registers
     */
    private int             m_easp1, m_easp2;
    private int             m_operandAddress;
    private int             m_opcode;
    private int             m_operand;
    private int             m_result;
    private int             m_NZFlags;

    /**
     * ALU look up tables
     */
    private int             m_BCDTableAdd[];               // addition
    // correction
    private int             m_BCDTableSub[];               // subtraction

    class ExecutionThread extends Thread
    {

        boolean stop = false;
        Object  lock = new Object();

        public ExecutionThread()
        {
            super( "CPU thread" );
            System.out.println( "Creating new CPU thread." );
        }

        public void run()
        {

            while ( !stop )
            {

                long s, e = 0;

                int ticks = m_clock;

                s = System.currentTimeMillis();
                while ( (m_clock - ticks) < 100000 )
                {
                    executeInstruction();
                }
                e = System.currentTimeMillis();

                long delay = 100 - (e - s);

                if ( delay > 0 )
                {
                    try
                    {
                        synchronized ( lock )
                        {
                            lock.wait( 100 - (e - s) );
                        }
                    }
                    catch ( InterruptedException ie )
                    {
                        ie.printStackTrace();
                        continue;
                    }
                }

            }

            System.out.println( "CPU thread Stopped" );
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

    private void incPC( int i )
    {
        m_PC += i;
        if ( m_PC > 0xffff )
        {
            m_PC = m_PC - 0xffff;
        }

    }

    private int increment( int value, int plus )
    {
        value += plus;
        if ( value > 0xffff )
        {
            value = value - 0xffff;
        }

        return value;
    }

    private int increment( int value, int plus1, int plus2 )
    {
        value += plus1 + plus2;

        if ( value > 0xffff )
        {
            value = value - 0xffff;
        }

        return value;
    }

    /**
     * Generic memory read & write (0x0000-0xffff)
     */
    protected int memoryRead( int addr )
    {

        return m_bus.read( addr ) & 0xff;

    }

    protected void memoryWrite( int addr, int value )
    {
        m_bus.write( addr, (byte) value );
    }

    /*
     * Zero page read & write
     */
    private final int zeroPageRead( int addr )
    {
        return m_bus.read( addr ) & 0xff;
    }

    private final void zeroPageWrite( int addr, int value )
    {
        m_bus.write( addr, (byte) value );
    }

    /**
     * Userspace interrupts
     */
    public final void assertReset()
    {
        m_exceptionRegister |= SIG_6502_RESET;
    }

    public final void assertNMI()
    {
        m_exceptionRegister |= SIG_6502_NMI;
    }

    public final void assertIRQ()
    {
        m_exceptionRegister |= SIG_6502_IRQ;
    }

    /**
     * Build the 6502.
     * 
     * @param bus
     */
    public CPU6502( Bus bus )
    {

        this.m_bus = bus;
        // Init BCD tables
        m_BCDTableAdd = new int[ 512 ];
        m_BCDTableSub = new int[ 512 ];

        for ( int i = 0; i < 512; i++ )
        {
            m_BCDTableAdd[ i ] = ( (i & 0x0f) <= 0x09) ? i : (i + 0x06);
            m_BCDTableAdd[ i ] += ( (m_BCDTableAdd[ i ] & 0xf0) <= 0x90)
                    ? 0
                    : 0x60;
            if ( m_BCDTableAdd[ i ] > 0x1ff )
                m_BCDTableAdd[ i ] -= 0x100;

            m_BCDTableSub[ i ] = ( (i & 0x0f) <= 0x09) ? i : (i - 0x06);
            m_BCDTableSub[ i ] -= ( (m_BCDTableSub[ i ] & 0xf0) <= 0x90)
                    ? 0
                    : 0x60;
        }
    }

    /*
     * Stack macros
     */
    private final int pop()
    {
        m_S++;
        m_S &= 0xff;
        return (memoryRead( m_S | 0x100 ) & 0xff);
    }

    private final void push( int value )
    {
        memoryWrite( m_S | 0x100, value );
        m_S--;
        m_S &= 0xff;
    }

    /*
     * Macros for P flags
     */
    private final void setN( boolean b )
    {
        if ( b )
            m_P |= FLAG_N;
        else
            m_P &= ~FLAG_N;
    }

    private final void setV( boolean b )
    {
        if ( b )
            m_P |= FLAG_V;
        else
            m_P &= ~FLAG_V;
    }

    private final void setB( boolean b )
    {
        if ( b )
            m_P |= FLAG_B;
        else
            m_P &= ~FLAG_B;
    }

    private final void setD( boolean b )
    {
        if ( b )
            m_P |= FLAG_D;
        else
            m_P &= ~FLAG_D;
    }

    private final void setI( boolean b )
    {
        if ( b )
            m_P |= FLAG_I;
        else
            m_P &= ~FLAG_I;
    }

    private final void setZ( boolean b )
    {
        if ( b )
            m_P |= FLAG_Z;
        else
            m_P &= ~FLAG_Z;
    }

    private final void setC( boolean b )
    {
        if ( b )
            m_P |= FLAG_C;
        else
            m_P &= ~FLAG_C;
    }

    private final boolean getN()
    {
        return ( (m_P & FLAG_N) != 0);
    }

    private final boolean getV()
    {
        return ( (m_P & FLAG_V) != 0);
    }

    private final boolean getD()
    {
        return ( (m_P & FLAG_D) != 0);
    }

    private final boolean getI()
    {
        return ( (m_P & FLAG_I) != 0);
    }

    private final boolean getZ()
    {
        return ( (m_P & FLAG_Z) != 0);
    }

    private final boolean getC()
    {
        return ( (m_P & FLAG_C) != 0);
    }

    /**
     * Fast condition codes. Instead of using bits to encode condition codes,
     * recent ALU results are cached to that the condition codes can be handled
     * more easily by the emulator's native hardware.
     */
    private final boolean getFN()
    {
        return ( (m_NZFlags & 0x280) != 0);
    }

    private final boolean getFNotN()
    {
        return ( (m_NZFlags & 0x280) == 0);
    }

    private final boolean getFZ()
    {
        return ( (m_NZFlags & 0xff) == 0);
    }

    private final boolean getFNotZ()
    {
        return ( (m_NZFlags & 0xff) != 0);
    }

    private final void setFNZ( boolean n, boolean z )
    {
        m_NZFlags = ( (n) ? 0x200 : 0x00) | ( (z) ? 0x00 : 0x01);
    }

    private final boolean getFC()
    {
        return (m_result >> 8) != 0;
    }

    private final boolean getFNotC()
    {
        return (m_result >> 8) == 0;
    }

    private final int getFC_()
    {
        return m_result >> 8;
    }

    private final void setFC( boolean c )
    {
        m_result = (c ? 0x100 : 0x00);
    }

    /*
     * Macros for effective address calculation (Macros whose names end with NC
     * do not check for page crossing)
     */
    private final int eaimm()
    {
        m_easp1 = memoryRead( m_PC );
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int eazp()
    {
        m_easp1 = memoryRead( m_PC );
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int eazpx()
    {
        m_easp1 = (increment( memoryRead( m_PC ), m_X )) & 0xff;
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int eazpy()
    {
        m_easp1 = (increment( memoryRead( m_PC ), m_Y )) & 0xff;
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int eaabs()
    {
        m_easp1 = memoryRead( m_PC );
        m_PC = increment( m_PC, 1 );
        m_easp1 = increment( m_easp1, memoryRead( m_PC ) << 8 );
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int earel()
    {
        // easp1 = memoryRead(PC); PC++;
        // return ((easp1 & 0x80) != 0) ? easp1 - 256 : easp1;
        m_easp1 = (byte) memoryRead( m_PC );
        m_PC = increment( m_PC, 1 );
        return m_easp1;
    }

    private final int eaabsx()
    {
        // No cross page check...
        // easp1 = eaabs();
        // checkCrossPage(easp1, X);
        // return easp1 + X;
        return increment( eaabs(), m_X );
    }

    private final int eaabsxNC()
    {
        return increment( eaabs(), m_X );
    }

    private final int eaabsy()
    {
        // No cross page check...
        // easp1 = eaabs();
        // checkCrossPage(easp1, Y);
        // return easp1 + Y;
        return increment( eaabs(), m_Y );
    }

    /*
     * Indirect addressing
     */
    private final int eaabsind()
    {
        m_easp1 = eaabs();
        m_easp2 = memoryRead( m_easp1 );
        return increment( m_easp2, (memoryRead( m_easp1 + 1 ) << 8) );
    }

    private final int eazpxind()
    {
        m_easp1 = eazpx();
        m_easp2 = zeroPageRead( m_easp1 );
        return increment(
                m_easp2,
                (zeroPageRead( increment( m_easp1, 1 ) & 0xff ) << 8) );
    }

    private final int eazpindy()
    {
        m_easp1 = eaimm();
        m_easp2 = zeroPageRead( m_easp1 );
        // No cross page check...
        // easp2 += (zeroPageRead((easp1 + 1) & 0xff) << 8);
        // checkCrossPage(easp2,Y);
        // return easp2 + Y;

        return increment(
                m_easp2,
                (zeroPageRead( increment( m_easp1, 1 ) & 0xff ) << 8),
                m_Y );
    }

    /*
     * New 65C02 addressing mode
     */
    private final int eazpind()
    {
        m_easp1 = eazp();
        m_easp2 = zeroPageRead( m_easp1 );
        return increment( m_easp2, (zeroPageRead( (m_easp1 + 1) & 0xff ) << 8) );
    }

    private final int eaabsxind()
    {
        m_easp1 = eaabs();
        m_easp2 = memoryRead( m_easp1 );
        return increment(
                m_easp2,
                (memoryRead( increment( m_easp1, 1 ) ) << 8),
                m_X );
    }

    /*
     * Misc. macros
     */
    private final void adcBCDAdjust()
    {
        if ( getD() )
            m_result = m_BCDTableAdd[ m_result ];
    }

    private final void sbcBCDAdjust()
    {
        if ( getD() )
            m_result = m_BCDTableSub[ m_result ];
    }

    private final void branch( int operand )
    {
        // No cross page check...
        // checkCrossPage(PC, operand);
        incPC( operand );
        m_clock++;
    }

    /** This executes a single instruction. */
    public final void executeInstruction()
    {

        checkInterrupts();

        m_opcode = memoryRead( m_PC );

        m_PC = increment( m_PC, 1 );

        switch ( m_opcode )
        {
            case 0x69: // ADC #imm
                m_operand = eaimm();
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x6D: // ADC abs
                m_operand = memoryRead( eaabs() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x65: // ADC zp
                m_operand = zeroPageRead( eazp() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0x61: // ADC (zp,X)
                m_operand = memoryRead( eazpxind() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0x71: // ADC (zp),Y
                m_operandAddress = eazpindy();
                m_operand = memoryRead( m_operandAddress );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x75: // ADC zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x7D: // ADC abs,X
                m_operand = memoryRead( eaabsx() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x79: // ADC abs,Y
                m_operand = memoryRead( eaabsy() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x29: // AND #imm
                m_A &= eaimm();
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x2D: // AND abs
                m_A &= memoryRead( eaabs() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x25: // AND zp
                m_A &= zeroPageRead( eazp() );
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0x21: // AND (zp,X)
                m_A &= memoryRead( eazpxind() );
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0x31: // AND (zp),Y
                m_A &= memoryRead( eazpindy() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x35: // AND zp,X
                m_A &= zeroPageRead( eazpx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x3D: // AND abs,X
                m_A &= memoryRead( eaabsx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x39: // AND abs,Y
                m_A &= memoryRead( eaabsy() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x0E: // ASL abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_result = m_operand << 1;
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x06: // ASL zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = m_operand << 1;
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 5;
                break;

            case 0x0A: // ASL acc
                m_result = m_A << 1;
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x16: // ASL zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = m_operand << 1;
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x1E: // ASL abs,X
                m_operandAddress = eaabsx();
                m_operand = memoryRead( m_operandAddress );
                m_result = m_operand << 1;
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 7;
                break;

            case 0x90: // BCC rr
                m_operand = earel();
                m_clock += 2;
                if ( getFNotC() )
                    branch( m_operand );
                break;

            case 0xB0: // BCS rr
                m_operand = earel();
                m_clock += 2;
                if ( getFC() )
                    branch( m_operand );
                break;

            case 0xF0: // BEQ rr
                m_operand = earel();
                m_clock += 2;
                if ( getFZ() )
                    branch( m_operand );
                break;

            case 0x2C: // BIT abs
                m_operand = memoryRead( eaabs() );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                m_clock += 4;
                break;

            case 0x24: // BIT zp
                m_operand = zeroPageRead( eazp() );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                m_clock += 3;
                break;

            case 0x30: // BMI rr
                m_operand = earel();
                m_clock += 2;
                if ( getFN() )
                    branch( m_operand );
                break;

            case 0xD0: // BNE rr
                m_operand = earel();
                m_clock += 2;
                if ( getFNotZ() )
                    branch( m_operand );
                break;

            case 0x10: // BPL rr
                m_operand = earel();
                m_clock += 2;
                if ( getFNotN() )
                    branch( m_operand );
                break;

            case 0x00: // BRK
                push( m_PC >> 8 ); // save PCH, PCL & P
                push( m_PC );
                setN( getFN() );
                setZ( getFZ() );
                setC( getFC() );
                setB( true );
                push( m_P );
                setI( true );
                m_PC = memoryRead( 0xfffe );
                m_PC |= memoryRead( 0xffff ) << 8;
                m_clock += 7;
                break;

            case 0x50: // BVC rr
                m_operand = earel();
                m_clock += 2;
                if ( !getV() )
                    branch( m_operand );
                break;

            case 0x70: // BVS rr
                m_operand = earel();
                m_clock += 2;
                if ( getV() )
                    branch( m_operand );
                break;

            case 0x18: // CLC rr
                setFC( false );
                m_clock += 2;
                break;

            case 0xD8: // CLD
                setD( false );
                m_clock += 2;
                break;

            case 0x58: // CLI
                setI( false );
                m_clock += 2;
                if ( m_pendingIRQ > 0 )
                {
                    m_pendingIRQ--;
                    assertIRQ();
                }
                break;

            case 0xB8: // CLV
                setV( false );
                m_clock += 2;
                break;

            case 0xC9: // CMP #imm
                m_result = 0x100 + m_A - eaimm();
                m_NZFlags = m_result;
                m_clock += 2;
                break;

            case 0xCD: // CMP abs
                m_result = 0x100 + m_A - memoryRead( eaabs() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xC5: // CMP zp
                m_result = 0x100 + m_A - zeroPageRead( eazp() );
                m_NZFlags = m_result;
                m_clock += 3;
                break;

            case 0xC1: // CMP (zp,X)
                m_result = 0x100 + m_A - memoryRead( eazpxind() );
                m_NZFlags = m_result;
                m_clock += 6;
                break;

            case 0xD1: // CMP (zp),Y
                m_result = 0x100 + m_A - memoryRead( eazpindy() );
                m_NZFlags = m_result;
                m_clock += 5;
                break;

            case 0xD5: // CMP zp,X
                m_result = 0x100 + m_A - zeroPageRead( eazpx() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xDD: // CMP abs,X
                m_result = 0x100 + m_A - memoryRead( eaabsx() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xD9: // CMP abs,Y
                m_result = 0x100 + m_A - memoryRead( eaabsy() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xE0: // CPX #imm
                m_result = 0x100 + m_X - eaimm();
                m_NZFlags = m_result;
                m_clock += 2;
                break;

            case 0xEC: // CPX abs
                m_result = 0x100 + m_X - memoryRead( eaabs() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xE4: // CPX zp
                m_result = 0x100 + m_X - zeroPageRead( eazp() );
                m_NZFlags = m_result;
                m_clock += 3;
                break;

            case 0xC0: // CPY #imm
                m_result = 0x100 + m_Y - eaimm();
                m_NZFlags = m_result;
                m_clock += 2;
                break;

            case 0xCC: // CPY abs
                m_result = 0x100 + m_Y - memoryRead( eaabs() );
                m_NZFlags = m_result;
                m_clock += 4;
                break;

            case 0xC4: // CPY zp
                m_result = 0x100 + m_Y - zeroPageRead( eazp() );
                m_NZFlags = m_result;
                m_clock += 3;
                break;

            case 0xCE: // DEC abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_NZFlags = m_operand + 0xff;
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0xC6: // DEC zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_NZFlags = m_operand + 0xff;
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 5;
                break;

            case 0xD6: // DEC zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_NZFlags = m_operand + 0xff;
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0xDE: // DEC abs,X
                m_operandAddress = eaabsx();
                m_operand = memoryRead( m_operandAddress );
                m_NZFlags = m_operand + 0xff;
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 7;
                break;

            case 0xCA: // DEX
                m_NZFlags = m_X + 0xff;
                m_X = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0x88: // DEY
                m_NZFlags = m_Y + 0xff;
                m_Y = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0x49: // EOR #imm
                m_A ^= eaimm();
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x4D: // EOR abs
                m_A ^= memoryRead( eaabs() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x45: // EOR zp
                m_A ^= zeroPageRead( eazp() );
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0x41: // EOR (zp,X)
                m_A ^= memoryRead( eazpxind() );
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0x51: // EOR (zp),Y
                m_A ^= memoryRead( eazpindy() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x55: // EOR zp,X
                m_A ^= zeroPageRead( eazpx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x5D: // EOR abs,X
                m_A ^= memoryRead( eaabsx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x59: // EOR abs,Y
                m_A ^= memoryRead( eaabsy() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xEE: // INC abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_NZFlags = m_operand + 1;
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0xE6: // INC zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_NZFlags = m_operand + 1;
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 5;
                break;

            case 0xF6: // INC zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_NZFlags = m_operand + 1;
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0xFE: // INC abs,X
                m_operandAddress = eaabsxNC();
                m_operand = memoryRead( m_operandAddress );
                m_NZFlags = m_operand + 1;
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 7;
                break;

            case 0xE8: // INX
                m_NZFlags = m_X + 1;
                m_X = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0xC8: // INY
                m_NZFlags = m_Y + 1;
                m_Y = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0x4C: // JMP abs
                m_PC = eaabs();
                m_clock += 3;
                break;

            case 0x6C: // JMP (abs)
                m_PC = eaabsind();
                m_clock += 5;
                break;

            case 0x20: // JSR abs
                m_operandAddress = eaabs();
                m_PC--;
                push( m_PC >> 8 );
                push( m_PC );
                m_PC = m_operandAddress;
                m_clock += 6;
                break;

            case 0xA9: // LDA #imm
                m_A = eaimm();
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0xAD: // LDA abs
                m_A = memoryRead( eaabs() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xA5: // LDA zp
                m_A = zeroPageRead( eazp() );
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0xA1: // LDA (zp,X)
                m_A = memoryRead( eazpxind() );
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0xB1: // LDA (zp),Y
                m_A = memoryRead( eazpindy() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0xB5: // LDA zp,X
                m_A = zeroPageRead( eazpx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xBD: // LDA abs,X
                m_A = memoryRead( eaabsx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xB9: // LDA abs,Y
                m_A = memoryRead( eaabsy() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xA2: // LDX #imm
                m_X = eaimm();
                m_NZFlags = m_X;
                m_clock += 2;
                break;

            case 0xAE: // LDX abs
                m_X = memoryRead( eaabs() );
                m_NZFlags = m_X;
                m_clock += 4;
                break;

            case 0xA6: // LDX zp
                m_X = zeroPageRead( eazp() );
                m_NZFlags = m_X;
                m_clock += 3;
                break;

            case 0xBE: // LDX abs,Y
                m_X = memoryRead( eaabsy() );
                m_NZFlags = m_X;
                m_clock += 4;
                break;

            case 0xB6: // LDX zp,Y
                m_X = zeroPageRead( eazpy() );
                m_NZFlags = m_X;
                m_clock += 4;
                break;

            case 0xA0: // LDY #imm
                m_Y = eaimm();
                m_NZFlags = m_Y;
                m_clock += 2;
                break;

            case 0xAC: // LDY abs
                m_Y = memoryRead( eaabs() );
                m_NZFlags = m_Y;
                m_clock += 4;
                break;

            case 0xA4: // LDY zp
                m_Y = zeroPageRead( eazp() );
                m_NZFlags = m_Y;
                m_clock += 3;
                break;

            case 0xB4: // LDY zp,X
                m_Y = zeroPageRead( eazpx() );
                m_NZFlags = m_Y;
                m_clock += 4;
                break;

            case 0xBC: // LDY abs,X
                m_Y = memoryRead( eaabsx() );
                m_NZFlags = m_Y;
                m_clock += 4;
                break;

            case 0x4E: // LSR abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_result = (m_operand & 0x01) << 8; // just get the C bit
                m_NZFlags = m_operand >> 1; // result in NZFlags
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0x46: // LSR zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = (m_operand & 0x01) << 8; // just get the C bit
                m_NZFlags = m_operand >> 1; // result in NZFlags
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 5;
                break;

            case 0x4A: // LSR acc
                m_result = (m_A & 0x01) << 8; // just get the C bit
                m_A >>= 1;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x56: // LSR zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = (m_operand & 0x01) << 8; // just get the C bit
                m_NZFlags = m_operand >> 1; // result in NZFlags
                zeroPageWrite( m_operandAddress, m_NZFlags );
                m_clock += 6;
                break;

            case 0x5E: // LSR abs,X
                m_operandAddress = eaabsx();
                m_operand = memoryRead( m_operandAddress );
                m_result = (m_operand & 0x01) << 8; // just get the C bit
                m_NZFlags = m_operand >> 1; // result in NZFlags
                memoryWrite( m_operandAddress, m_NZFlags );
                m_clock += 7;
                break;

            case 0xEA: // NOP
                m_clock += 2;
                break;

            case 0x09: // ORA #imm
                m_A |= eaimm();
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x0D: // ORA abs
                m_A |= memoryRead( eaabs() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x05: // ORA zp
                m_A |= zeroPageRead( eazp() );
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0x01: // ORA (zp,X)
                m_A |= memoryRead( eazpxind() );
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0x11: // ORA (zp),Y
                m_A |= memoryRead( eazpindy() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x15: // ORA zp,X
                m_A |= zeroPageRead( eazpx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x1D: // ORA abs,X
                m_A |= memoryRead( eaabsx() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x19: // ORA abs,Y
                m_A |= memoryRead( eaabsy() );
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x48: // PHA
                push( m_A );
                m_clock += 3;
                break;

            case 0x08: // PHP
                setN( getFN() );
                setZ( getFZ() );
                setC( getFC() );
                push( m_P );
                m_clock += 3;
                break;

            case 0x68: // PLA
                m_A = pop();
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x28: // PLP
                m_P = pop() | 0x20; // fix bug in bit5 of P
                setFC( getC() );
                setFNZ( getN(), getZ() );
                m_clock += 4;
                if ( (m_pendingIRQ > 0) && !getI() )
                {
                    m_pendingIRQ--;
                    assertIRQ();
                }
                break;

            case 0x2E: // ROL abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_result = (m_operand << 1) | getFC_();
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x26: // ROL zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = (m_operand << 1) | getFC_();
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 5;
                break;

            case 0x2A: // ROL acc
                m_result = (m_A << 1) | getFC_();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x36: // ROL zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = (m_operand << 1) | getFC_();
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x3E: // ROL abs,X
                m_operandAddress = eaabsx();
                m_operand = memoryRead( m_operandAddress );
                m_result = (m_operand << 1) | getFC_();
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 7;
                break;

            case 0x6E: // ROR abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                m_result = ( (m_operand & 0x01) << 8)
                        | (getFC_() << 7)
                        | (m_operand >> 1);
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x66: // ROR zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = ( (m_operand & 0x01) << 8)
                        | (getFC_() << 7)
                        | (m_operand >> 1);
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 5;
                break;

            case 0x6A: // ROR acc
                m_result = ( (m_A & 0x01) << 8) | (getFC_() << 7) | (m_A >> 1);
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x76: // ROR zp,X
                m_operandAddress = eazpx();
                m_operand = zeroPageRead( m_operandAddress );
                m_result = ( (m_operand & 0x01) << 8)
                        | (getFC_() << 7)
                        | (m_operand >> 1);
                m_NZFlags = m_result;
                zeroPageWrite( m_operandAddress, m_result );
                m_clock += 6;
                break;

            case 0x7E: // ROR abs,X
                m_operandAddress = eaabsx();
                m_operand = memoryRead( m_operandAddress );
                m_result = ( (m_operand & 0x01) << 8)
                        | (getFC_() << 7)
                        | (m_operand >> 1);
                m_NZFlags = m_result;
                memoryWrite( m_operandAddress, m_result );
                m_clock += 7;
                break;

            case 0x40: // RTI
                m_P = pop() | 0x20; // bit 5 bug of 6502
                setFC( getC() );
                setFNZ( getN(), getZ() );
                m_PC = pop(); // splitting is necessary
                m_PC += pop() << 8; // because of nested macros
                m_clock += 6;
                break;

            case 0x60: // RTS
                m_PC = pop(); // splitting is necessary
                m_PC += pop() << 8; // because of nested macros
                incPC( 1 );
                m_clock += 6;
                break;

            case 0xE9: // SBC #imm
                m_operand = 255 - eaimm();
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0xED: // SBC abs
                m_operand = 255 - memoryRead( eaabs() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xE5: // SBC zp
                m_operand = 255 - zeroPageRead( eazp() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 3;
                break;

            case 0xE1: // SBC (zp,X)
                m_operand = 255 - memoryRead( eazpxind() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 6;
                break;

            case 0xF1: // SBC (zp),Y
                m_operand = 255 - memoryRead( eazpindy() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0xF5: // SBC zp,X
                m_operand = 255 - zeroPageRead( eazpx() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xFD: // SBC abs,X
                m_operand = 255 - memoryRead( eaabsx() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0xF9: // SBC abs,Y
                m_operand = 255 - memoryRead( eaabsy() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 4;
                break;

            case 0x38: // SEC
                setFC( true );
                m_clock += 2;
                break;

            case 0xF8: // SED
                setD( true );
                m_clock += 2;
                break;

            case 0x78: // SEI
                setI( true );
                m_clock += 2;
                break;

            case 0x8D: // STA abs
                memoryWrite( eaabs(), m_A );
                m_clock += 4;
                break;

            case 0x85: // STA zp
                zeroPageWrite( eazp(), m_A );
                m_clock += 3;
                break;

            case 0x81: // STA (zp,X)
                memoryWrite( eazpxind(), m_A );
                m_clock += 6;
                break;

            case 0x91: // STA (zp),Y
                memoryWrite( eazpindy(), m_A );
                m_clock += 6;
                break;

            case 0x95: // STA zp,X
                zeroPageWrite( eazpx(), m_A );
                m_clock += 4;
                break;

            case 0x9D: // STA abs,X
                memoryWrite( eaabsx(), m_A );
                m_clock += 5;
                break;

            case 0x99: // STA abs,Y
                memoryWrite( eaabsy(), m_A );
                m_clock += 5;
                break;

            case 0x8E: // STX abs
                memoryWrite( eaabs(), m_X );
                m_clock += 4;
                break;

            case 0x86: // STX zp
                zeroPageWrite( eazp(), m_X );
                m_clock += 3;
                break;

            case 0x96: // STX zp,Y
                zeroPageWrite( eazpy(), m_X );
                m_clock += 4;
                break;

            case 0x8C: // STY abs
                memoryWrite( eaabs(), m_Y );
                m_clock += 4;
                break;

            case 0x84: // STY zp
                zeroPageWrite( eazp(), m_Y );
                m_clock += 3;
                break;

            case 0x94: // STY zp,X
                zeroPageWrite( eazpx(), m_Y );
                m_clock += 4;
                break;

            case 0xAA: // TAX
                m_X = m_A;
                m_NZFlags = m_X;
                m_clock += 2;
                break;

            case 0xA8: // TAY
                m_Y = m_A;
                m_NZFlags = m_Y;
                m_clock += 2;
                break;

            case 0xBA: // TSX
                m_X = m_S;
                m_NZFlags = m_X;
                m_clock += 2;
                break;

            case 0x8A: // TXA
                m_A = m_X;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            case 0x9A: // TXS
                m_S = m_X;
                m_clock += 2;
                break;

            case 0x98: // TYA
                m_A = m_Y;
                m_NZFlags = m_A;
                m_clock += 2;
                break;

            /*
             * 65C02 instructions note: timing is not correct
             */

            case 0x72: // ADC (zp)
                m_operand = memoryRead( eazpind() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                adcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x32: // AND (zp)
                m_A &= memoryRead( eazpind() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x34: // BIT zp,X
                m_operand = zeroPageRead( eazpx() );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                m_clock += 3;
                break;

            case 0x89: // BIT #imm
                m_operand = eaimm();
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                m_clock += 2;
                break;

            case 0x3C: // BIT abs,X
                m_operand = eaabsx();
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                m_clock += 4;
                break;

            case 0x80: // BRA rr
                m_operand = earel();
                m_clock += 2;
                branch( m_operand );
                break;

            case 0xD2: // CMP (zp)
                m_result = 0x100 + m_A - memoryRead( eazpind() );
                m_NZFlags = m_result;
                m_clock += 5;
                break;

            case 0x3A: // DEA acc
                m_NZFlags = m_A + 0xff;
                m_A = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0x52: // EOR (zp)
                m_A ^= memoryRead( eazpind() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x1A: // INA acc
                m_NZFlags = m_A + 1;
                m_A = m_NZFlags & 0xff;
                m_clock += 2;
                break;

            case 0x7C: // JMP (abs,X)
                m_PC = eaabsxind();
                m_clock += 6;
                break;

            case 0xB2: // LDA (zp)
                m_A = memoryRead( eazpind() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x12: // ORA (zp)
                m_A |= memoryRead( eazpind() );
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0xDA: // PHX
                push( m_X );
                m_clock += 3;
                break;

            case 0xFA: // PLX
                m_X = pop();
                m_NZFlags = m_X;
                m_clock += 4;
                break;

            case 0x5A: // PHY
                push( m_Y );
                m_clock += 3;
                break;

            case 0x7A: // PLY
                m_Y = pop();
                m_NZFlags = m_Y;
                m_clock += 4;
                break;

            case 0xF2: // SBC (zp)
                m_operand = 255 - memoryRead( eazpind() );
                m_result = m_operand + m_A + getFC_();
                setV( ! ( ( (m_operand ^ m_A) & 0x80) != 0)
                        && ( ( (m_A ^ m_result) & 0x80) != 0) );
                sbcBCDAdjust();
                m_A = m_result & 0xff;
                m_NZFlags = m_A;
                m_clock += 5;
                break;

            case 0x92: // STA (zp)
                memoryWrite( eazpind(), m_A );
                m_clock += 6;
                break;

            case 0x9C: // STZ abs
                memoryWrite( eaabs(), 0 );
                m_clock += 4;
                break;

            case 0x64: // STZ zp
                zeroPageWrite( eazp(), 0 );
                m_clock += 3;
                break;

            case 0x74: // STZ zp,X
                zeroPageWrite( eazpx(), 0 );
                m_clock += 3;
                break;

            case 0x9E: // STZ abs,X
                memoryWrite( eaabsx(), 0 );
                m_clock += 4;
                break;

            case 0x1C: // TRB abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                memoryWrite( m_operandAddress, (m_operand & ~m_A) & 0xff );
                m_clock += 5;
                break;

            case 0x14: // TRB zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                zeroPageWrite( m_operandAddress, (m_operand & ~m_A) & 0xff );
                m_clock += 5;
                break;

            case 0x0C: // TSB abs
                m_operandAddress = eaabs();
                m_operand = memoryRead( m_operandAddress );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                memoryWrite( m_operandAddress, m_operand | m_A );
                m_clock += 5;
                break;

            case 0x04: // TSB zp
                m_operandAddress = eazp();
                m_operand = zeroPageRead( m_operandAddress );
                setV( (m_operand & 0x40) != 0 );
                m_NZFlags = ( (m_operand & 0x80) << 2) | (m_A & m_operand);
                zeroPageWrite( m_operandAddress, m_operand | m_A );
                m_clock += 5;
                break;

            default: // unknown instructions
                System.out.println( "Unknown instruction:"
                        + Integer.toHexString( m_opcode ) );
                m_clock += 2;
        }
    }

    public final void checkInterrupts()
    {
        // Reset
        if ( (m_exceptionRegister & SIG_6502_RESET) != 0 )
        {
            System.out.println( "CPU Reset" );
            m_A = m_X = m_Y = 0;
            m_P = 0x20;
            setFC( getC() );
            setFNZ( getN(), getZ() );
            m_S = 0xff;
            m_PC = memoryRead( 0xfffc );
            m_PC |= (memoryRead( 0xfffd ) << 8);
            m_exceptionRegister &= ~SIG_6502_RESET;
        }

        // No NMI nor IRQ...
        if ( (m_exceptionRegister & SIG_6502_NMI) != 0 )
        {
            push( m_PC >> 8 );
            push( m_PC );
            setN( getFN() );
            setZ( getFZ() );
            setC( getFC() );
            push( m_P );
            m_PC = memoryRead( 0xfffa );
            m_PC |= memoryRead( 0xfffb ) << 8;
            m_clock += 7;
            m_exceptionRegister ^= SIG_6502_NMI;
        }

        if ( (m_exceptionRegister & SIG_6502_IRQ) != 0 )
        {
            if ( getI() )
                m_pendingIRQ++;
            else
            {
                push( m_PC >> 8 );
                push( m_PC );
                setN( getFN() );
                setZ( getFZ() );
                setC( getFC() );
                setB( false );
                push( m_P );
                setI( true );
                m_PC = memoryRead( 0xfffe );
                m_PC |= memoryRead( 0xffff ) << 8;
                m_clock += 7;
            }
            m_exceptionRegister ^= SIG_6502_IRQ;
        }
    }

    public int getClock()
    {
        return m_clock;
    }

    public synchronized void stop()
    {
        if ( m_executionThread == null )
        {
            return;
        }

        m_executionThread.stopExecution();
        m_executionThread = null;
    }

    public synchronized void start()
    {

        if ( m_executionThread != null )
        {
            return;
        }

        m_executionThread = new ExecutionThread();
        m_executionThread.start();

    }

}
