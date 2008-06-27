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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;




/**
 * TK2000 Keyboard emulation.
 * 
 * In the TK2000 the keyboard is implemented as a 8x8 matrix. The matrix is fed
 * on KBIN with a scan of "1" and KBOUT is read looking for a pressed key.
 * 
 * 
 *   Keyboard Matrix
 *   ===============
 * 
 *                         KBIN                     
 *             0   1   2   3   4   5   6   7
 *         0       B   V   C   X   Z
 *     K   1       G   F   D   S   A
 *     B   2  " "  T   R   E   W   Q 
 *     O   3  LFT  5   4   3   2   1
 *     U   4  RGT  6   7   8   9   0
 *     T   5  DWN  Y   U   I   O   P 
 *         6  UP   H   J   K   L   :
 *         7  RTN  N   M   ,   .   ?
 *
 * 
 * 
 * 
 *   PC to TK2000 keyboard maps.
 *   ===========================
 *   
 *   Backspace = Left
 *   = = SHIFT+O  
 *   + = SHIFT+P
 *   - = SHIFT+I
 *   @ = SHIFT+L
 *   ^ = SHIFT+K
 *   ! = SHIFT+1
 *   " = SHIFT+2
 *   # = SHIFT+3
 *   $ = SHIFT+4
 *   % = SHIFT+5    
 *   & = SHIFT+6
 *   ' = SHIFT+7
 *   ( = SHIFT+8
 *   ) = SHIFT+9
 *   * = SHIFT+0
 * 
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 */
public class Keyboard implements Device, KeyListener
{

    CPU6502 m_cpu;
    boolean m_ctrl  = false;
    boolean m_shift = false;
    boolean m_clear = false;
    int     m_row;
    int     m_column;
    byte    m_kbOut;
    boolean m_kbOutCtrl;

    public Keyboard( CPU6502 cpu )
    {

        m_cpu = cpu;
    }

    /**
     * Read the KBOUT or ctrl line address.
     * 
     * @param addr
     *            The mem addr.
     */
    public synchronized byte read( int addr )
    {

        if ( m_clear )
        {
            return 0;
        }

        if ( m_kbOutCtrl && m_ctrl )
        {
            return 1;
        }

        if ( m_kbOut == 1 && m_shift )
        {
            return 1;
        }

        if ( m_kbOut == (byte) (1 << m_row) )
        {
            return (byte) (1 << m_column);
        }

        return 0;

    }

    /**
     * Write the KBIN or ctrl line addr.
     * 
     * @param addr
     *            The memory address.
     * @param value
     *            Byte to write.
     */
    public synchronized void write( int addr, byte value )
    {

        m_kbOutCtrl = false;

        if ( addr == 0xC05F )
        {
            m_kbOutCtrl = true;
            return;
        }

        m_kbOut = value;

        return;

    }

    /**
     * Map a key to a row,column value.
     * 
     * @param event
     */
    private void mapKey( KeyEvent event )
    {

        m_ctrl = false;
        m_shift = false;
        m_clear = false;

        if ( event.isControlDown() )
        {
            m_ctrl = true;
        }

        switch ( Character.toUpperCase( event.getKeyChar() ) )
        {

            case 'A':
                m_row = 1;
                m_column = 5;
                return;
            case 'B':
                m_row = 0;
                m_column = 1;
                return;
            case 'C':
                m_row = 0;
                m_column = 3;
                return;

            case 'D':
                m_row = 1;
                m_column = 3;
                return;

            case 'E':
                m_row = 2;
                m_column = 3;
                return;
            case 'F':
                m_row = 1;
                m_column = 2;
                return;
            case 'G':
                m_row = 1;
                m_column = 1;
                return;

            case 'H':
                m_row = 6;
                m_column = 1;
                return;

            case 'I':
                m_row = 5;
                m_column = 3;
                return;
            case 'J':
                m_row = 6;
                m_column = 2;
                return;

            case 'K':
                m_row = 6;
                m_column = 3;
                return;
            case 'L':
                m_row = 6;
                m_column = 4;
                return;
            case 'M':
                m_row = 7;
                m_column = 2;
                return;
            case 'N':
                m_row = 7;
                m_column = 1;
                return;
            case 'O':
                m_row = 5;
                m_column = 4;
                return;
            case 'P':
                m_row = 5;
                m_column = 5;
                return;
            case 'Q':
                m_row = 2;
                m_column = 5;
                return;
            case 'R':
                m_row = 2;
                m_column = 2;
                return;
            case 'S':
                m_row = 1;
                m_column = 4;
                return;
            case 'T':
                m_row = 2;
                m_column = 1;
                return;
            case 'U':
                m_row = 5;
                m_column = 2;
                return;
            case 'V':
                m_row = 0;
                m_column = 2;
                return;
            case 'W':
                m_row = 2;
                m_column = 4;
                return;
            case 'X':
                m_row = 0;
                m_column = 4;
                return;
            case 'Y':
                m_row = 1;
                m_column = 3;
                return;
            case 'Z':
                m_row = 5;
                m_column = 1;
                return;

            case '1':
                m_row = 3;
                m_column = 5;
                return;
            case '2':
                m_row = 3;
                m_column = 4;
                return;
            case '3':
                m_row = 3;
                m_column = 3;
                return;
            case '4':
                m_row = 3;
                m_column = 2;
                return;
            case '5':
                m_row = 3;
                m_column = 1;
                return;
            case '6':
                m_row = 4;
                m_column = 1;
                return;
            case '7':
                m_row = 4;
                m_column = 2;
                return;
            case '8':
                m_row = 4;
                m_column = 3;
                return;
            case '9':
                m_row = 4;
                m_column = 4;
                return;
            case '0':
                m_row = 4;
                m_column = 5;
                return;

            case ',':
                m_row = 7;
                m_column = 3;
                return;
            case '.':
                m_row = 7;
                m_column = 4;
                return;

            case ':':
                m_row = 6;
                m_column = 5;
                return;

            case '?':
                m_row = 7;
                m_column = 5;
                return;

            case '!':
                m_row = 3;
                m_column = 5;
                m_shift = true;
                return;

            case '"':
                m_row = 3;
                m_column = 4;
                m_shift = true;
                return;

            case '#':
                m_row = 3;
                m_column = 3;
                m_shift = true;
                return;

            case '$':
                m_shift = true;
                m_row = 3;
                m_column = 2;
                return;

            case '%':
                m_row = 3;
                m_column = 1;
                m_shift = true;
                return;

            case '&':
                m_shift = true;
                m_row = 4;
                m_column = 1;
                return;

            case '/':
                m_row = 4;
                m_column = 2;
                m_shift = true;
                return;

            case '(':
                m_shift = true;
                m_row = 4;
                m_column = 3;
                return;

            case ')':
                m_shift = true;
                m_row = 4;
                m_column = 4;
                return;

            case '=':
                m_shift = true;
                m_row = 5;
                m_column = 4;
                return;

            case '-':
                m_shift = true;
                m_row = 5;
                m_column = 3;
                return;
            case '+':
                m_shift = true;
                m_row = 5;
                m_column = 5;
                return;
            case '*':
                m_shift = true;
                m_row = 4;
                m_column = 5;
                return;

            case '^':
                m_shift = true;
                m_row = 6;
                m_column = 3;
                return;

            case '@':
                m_shift = true;
                m_row = 6;
                m_column = 4;
                return;

        }

        switch ( event.getKeyCode() )
        {
            case KeyEvent.VK_UP:
                m_row = 6;
                m_column = 0;
                return;
            case KeyEvent.VK_DOWN:
                m_row = 5;
                m_column = 0;
                return;
            case KeyEvent.VK_LEFT:
                m_row = 3;
                m_column = 0;
                return;
            case KeyEvent.VK_RIGHT:
                m_row = 4;
                m_column = 0;
                return;
            case KeyEvent.VK_ENTER:
                m_row = 7;
                m_column = 0;
                return;
            case KeyEvent.VK_BACK_SPACE:
                m_row = 3;
                m_column = 0;
                return;
            case KeyEvent.VK_SPACE:
                m_row = 2;
                m_column = 0;
                return;
        }

        m_clear = true;
        return;

    }

    public synchronized void keyPressed( KeyEvent event )
    {
        mapKey( event );
    }

    public synchronized void keyReleased( KeyEvent event )
    {
        m_clear = true;
    }

    public void keyTyped( KeyEvent event )
    {

    }

}
