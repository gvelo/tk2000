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

/**
 * The TK2000 ROM memory.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 */
public class ROM implements Device
{

    byte[] m_rom = new byte[ 16 * 1024 ];

    public ROM() throws Exception
    {

        InputStream is = getClass().getResourceAsStream( "/TK2000.rom" );

        int readed = 0;

        while ( readed < m_rom.length )
        {
            readed += is.read( m_rom, readed, m_rom.length - readed );
        }

        is.close();

    }

    public byte read( int addr )
    {
        return m_rom[ addr - 0xC000 ];
    }

    public void write( int addr, byte value )
    {
        return;
    }

}
