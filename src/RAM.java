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
 * Represent the RAM array.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 */
public class RAM implements Device
{
    
    /* 64kb array */
    byte[] m_mem = new byte[ 1024 * 64 ];

    public byte read( int addr )
    {
        return m_mem[ addr ];
    }

    public void write( int addr, byte value )
    {
        m_mem[ addr ] = value;
    }

    /**
     * Clear RAM memory.
     *
     */
    public void clear()
    {
        System.out.println( "RAM cleared." );
        m_mem = new byte[ 1024 * 64 ];
    }

}
