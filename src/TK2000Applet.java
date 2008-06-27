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

import javax.swing.JApplet;
import javax.swing.UIManager;

/**
 * An applet as a container for the emulator.
 * 
 * @author Gabriel Velo <gabriel.velo@gmail.com>
 * 
 */
public class TK2000Applet extends JApplet
{

    private TK2000 m_tk2000;

    public void init()
    {
        try
        {
            UIManager
                    .setLookAndFeel( "javax.swing.plaf.metal.MetalLookAndFeel" );
            UIManager.put( "swing.boldMetal", Boolean.FALSE );
            m_tk2000 = new TK2000( getContentPane() );
            m_tk2000.run();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

    }

    public void destroy()
    {
        m_tk2000.stop();
        super.destroy();
    }

}
