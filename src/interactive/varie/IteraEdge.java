package interactive.varie;

/*
 *  Copyright (C) 2014  Damiano Bolla  website www.engidea.com
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
 *
 */

import freert.planar.PlaPointFloat;
import freert.varie.Signum;

public final class IteraEdge implements Comparable<IteraEdge>
   {
   public final IteraNetItem from_item;
   public final PlaPointFloat from_corner;
   public final IteraNetItem to_item;
   public final PlaPointFloat to_corner;
   public final double length_square;

   public IteraEdge(IteraNetItem p_from_item, PlaPointFloat p_from_corner, IteraNetItem p_to_item, PlaPointFloat p_to_corner)
      {
      from_item = p_from_item;
      from_corner = p_from_corner;
      to_item = p_to_item;
      to_corner = p_to_corner;
      length_square = p_to_corner.distance_square(p_from_corner);
      }

   /**
    * prevent result 0, so that edges with the same length as another edge are not skipped in the set
    */
   public int compareTo(IteraEdge p_other)
      {
      double result = length_square - p_other.length_square;
      if (result != 0)  return Signum.as_int(result);

      result = from_corner.v_x - p_other.from_corner.v_x;
      if (result != 0) return Signum.as_int(result);
      
      result = from_corner.v_y - p_other.from_corner.v_y;
      if (result != 0) return Signum.as_int(result);

      result = to_corner.v_x - p_other.to_corner.v_y;
      if (result != 0) return Signum.as_int(result);

      result = to_corner.v_y - p_other.to_corner.v_x;
      if (result != 0) return Signum.as_int(result);

      return 1;
      }
   }
