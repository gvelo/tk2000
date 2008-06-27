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
 * The memory bank softswitch select the memory bank (RAM or ROM ) mapped 
 * in the range 0xC100-0xFFFF.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 */
public class BankSW implements Device
{

    public static final int BANK_RAM = 0xC05B;
    public static final int BANK_ROM = 0xC05A;

    private RAM             m_ram;
    private ROM             m_rom;
    private Bus             m_bus;
    private int             m_mem_mode;

    public BankSW( Bus bus, RAM ram, ROM rom )
    {
        m_bus = bus;
        m_ram = ram;
        m_rom = rom;
        m_mem_mode = BANK_ROM;
    }

    public byte read( int addr )
    {        
        // read or write is the same.
        write( addr, (byte) 0 );
        return (byte) 0xFF;
    }

    public void write( int addr, byte value )
    {

        if ( addr == BANK_ROM )
        {

            if ( m_mem_mode == BANK_ROM )
            {
                return;
            }            

            System.out.println( "Switching to ROM" );

            m_mem_mode = BANK_ROM;
            

            if ( m_bus.getDevice( 0xC101 ) == m_ram )
            {
                // There aren't expansion cartridge in the slot.

                m_bus.setDevice( 0xC100, 0xFFFF, m_rom );
                return;

            }
            else
            {
                // There are expansion cartridge in the slot.
                m_bus.setDevice( 0xC200, 0xFFFF, m_rom );
                return;
            }

        }

        if ( addr == BANK_RAM )
        {

            if ( m_mem_mode == BANK_RAM )
            {
                return;
            }
            
            m_mem_mode = BANK_RAM;
            
            System.out.println( "Switching to RAM" );

            if ( m_bus.getDevice( 0xC101 ) == m_rom )
            {
                // There aren't cartridge in the slot.

                m_bus.setDevice( 0xC100, 0xFFFF, m_ram );
                return;

            }
            else
            {
                // There aren't cartridge in the slot.
                m_bus.setDevice( 0xC200, 0xFFFF, m_ram );
                return;
            }

        }

        

    }

}
