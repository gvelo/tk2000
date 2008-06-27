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

import java.util.ArrayList;

/**
 * This class represent the system bus. The computer devices are mapped to a
 * range of address in this bus. More than one device can be mapped to an
 * address.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class Bus
{

    private Object[] addreses = new Object[ 0x10000 ];

    public Bus()
    {
    }

    /**
     * Add a Device to the specified address.
     * 
     * @param addr
     * @param dev
     */
    public void addDevice( int addr, Device dev )
    {
        if ( addreses[ addr ] == null )
        {
            addreses[ addr ] = dev;
        }
        else
            /* if a device exist at this addr we build a list of devices. */
            if ( addreses[ addr ] instanceof Device )
            {
                ArrayList<Device> devices = new ArrayList<Device>();
                devices.add( (Device) addreses[ addr ] );
                devices.add( dev );
                addreses[ addr ] = devices;
            }
            else
                /* if it is a list just add the device to the list */
                if ( addreses[ addr ] instanceof ArrayList )
                {
                    ArrayList<Device> devices = (ArrayList<Device>) addreses[ addr ];
                    devices.add( dev );
                }
    }

    /**
     * Add a device to a range of address.
     * 
     * @param addrFrom
     * @param addrTo
     * @param dev
     */
    public void addDevice( int addrFrom, int addrTo, Device dev )
    {
        for ( int i = addrFrom; i <= addrTo; i++ )
        {
            addDevice( i, dev );
        }
    }

    /**
     * Add a device to the specified address. If a device is already mapped in
     * this address this is remplaced by dev.
     * 
     * @param addr
     * @param dev
     */
    public void setDevice( int addr, Device dev )
    {
        addreses[ addr ] = dev;
    }

    /**
     * Add a device to the specified range address. If a device is already
     * mapped in this range this is remplaced by dev.
     * 
     * @param addrFrom
     * @param addrTo
     * @param dev
     */
    public void setDevice( int addrFrom, int addrTo, Device dev )
    {
        for ( int i = addrFrom; i <= addrTo; i++ )
        {
            setDevice( i, dev );
        }
    }

    /**
     * Return the device or the List of devices mapped to addr.
     * 
     * @param addr
     * @return
     */
    public Object getDevice( int addr )
    {
        return addreses[ addr ];
    }

    /**
     * Make a read operation on addr.
     * 
     * @param addr
     * @return
     */
    public byte read( int addr )
    {

        Object device = addreses[ addr ];

        if ( device == null )
        {
            return (byte) 0xFF;
        }

        if ( device instanceof Device )
        {
            return ((Device) device).read( addr );
        }

        byte result = 0;

        for ( Device dev : ((ArrayList<Device>) device) )
        {
            result |= dev.read( addr );
        }

        return result;

    }

    /**
     * Make a write operation on addr.
     * 
     * @param addr
     * @param value
     */
    public void write( int addr, byte value )
    {

        Object device = addreses[ addr ];

        if ( device == null )
        {
            return;
        }

        if ( device instanceof Device )
        {
            ((Device) device).write( addr, value );
            return;
        }

        for ( Device dev : ((ArrayList<Device>) device) )
        {
            dev.write( addr, value );
        }

    }

}
