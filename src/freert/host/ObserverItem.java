/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 * Created on 20. September 2007, 07:39
 *
 */

package freert.host;

import board.infos.BrdComponent;
import board.items.BrdItem;

/**
 * Is this thing actually used ?
 * @author alfons
 */
public interface ObserverItem extends Observer<BrdItem>
   {
   /**
    * Enable the observers to synchronize the moved component.
    */
   public void notify_moved(BrdComponent p_component);
   }
