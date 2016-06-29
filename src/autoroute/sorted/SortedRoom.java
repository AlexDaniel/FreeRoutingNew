package autoroute.sorted;
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

import freert.planar.PlaDirection;
import freert.planar.PlaLineInt;
import freert.planar.PlaPointFloat;
import freert.planar.PlaPointInt;
import freert.planar.PlaSide;
import freert.planar.ShapeTile;
import freert.varie.Signum;

/**
 * Helper class to sort the doors of an expansion room counterclockwise amount the border of the room shape.
 * This is for the no angle algorithm
 * @author Alfons Wirtz
 */

public final class SortedRoom implements Comparable<SortedRoom>
   {
   static private final double c_dist_tolerance = 1;

   private final ShapeTile room_shape;
   // The shape of the neighbour room
   public final ShapeTile neighbour_shape;
   // The side number of this room, where it touches the neighbour
   public final int touching_side_no_of_room;
   // The side number of the neighbour room, where it touches this room
   public final int touching_side_no_of_neighbour_room;
   // True, if the intersection of this room and the neighbour is equal to a corner of this room
   public final boolean room_touch_is_corner;
   // True, if the intersection of this room and the neighbour is equal to a corner of the neighbour room
   public final boolean neighbour_room_touch_is_corner;

   private PlaPointInt precalculated_first_corner = null;
   private PlaPointInt precalculated_last_corner = null;

   
   SortedRoom(
         ShapeTile p_room_shape, 
         ShapeTile p_neighbour_shape, 
         int p_touching_side_no_of_room, 
         int p_touching_side_no_of_neighbour_room, 
         boolean p_room_touch_is_corner,
         boolean p_neighbour_room_touch_is_corner)
      {
      room_shape = p_room_shape;
      neighbour_shape = p_neighbour_shape;
      touching_side_no_of_room = p_touching_side_no_of_room;
      touching_side_no_of_neighbour_room = p_touching_side_no_of_neighbour_room;
      room_touch_is_corner = p_room_touch_is_corner;
      neighbour_room_touch_is_corner = p_neighbour_room_touch_is_corner;
      }

   /**
    * Compare function for or sorting the neighbours in counterclock sense around the border of the room shape 
    * in ascending order.
    */
   @Override
   public int compareTo(SortedRoom p_other)
      {
      int compare_value = touching_side_no_of_room - p_other.touching_side_no_of_room;

      if (compare_value != 0) return compare_value;

      PlaPointFloat compare_corner = room_shape.corner_approx(touching_side_no_of_room);
      double this_distance = first_corner().to_float().distance(compare_corner);
      double other_distance = p_other.first_corner().to_float().distance(compare_corner);
      double delta_distance = this_distance - other_distance;

      if (Math.abs(delta_distance) > c_dist_tolerance) return Signum.as_int(delta_distance);
      
      // check corners for equality
      if (first_corner().equals(p_other.first_corner()))
         {
         // in this case compare the last corners
         double this_distance2 = last_corner().to_float().distance(compare_corner);
         double other_distance2 = p_other.last_corner().to_float().distance(compare_corner);
         delta_distance = this_distance2 - other_distance2;
         if (Math.abs(delta_distance) <= c_dist_tolerance)
            {
            if ( neighbour_room_touch_is_corner && p_other.neighbour_room_touch_is_corner)
            // Otherwise there may be a short 1 dim. touch at a link between 2 trace lines.
            // In this case equality is ok, because the 2 intersection pieces with
            // the expansion room are identical, so that only 1 obstacle is needed.
               {
               int compare_line_no = touching_side_no_of_room;
               if (room_touch_is_corner)
                  {
                  compare_line_no = room_shape.prev_no(compare_line_no);
                  }
               PlaDirection compare_dir = room_shape.border_line(compare_line_no).direction().opposite();
               PlaLineInt this_compare_line =  neighbour_shape.border_line( touching_side_no_of_neighbour_room);
               PlaLineInt other_compare_line = p_other.neighbour_shape.border_line(p_other.touching_side_no_of_neighbour_room);
               delta_distance = compare_dir.compare_from(this_compare_line.direction(), other_compare_line.direction());
               }
            }
         }

      return Signum.as_int(delta_distance);
      }

   /**
    * @return the first corner of the intersection shape with the neighbour.
    */
   PlaPointInt first_corner()
      {
      if (precalculated_first_corner != null) return precalculated_first_corner;

      if (room_touch_is_corner)
         {
         precalculated_first_corner = room_shape.corner(touching_side_no_of_room);
         }
      else if (neighbour_room_touch_is_corner)
         {
         precalculated_first_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
         }
      else
         {
         PlaPointInt curr_first_corner = neighbour_shape.corner(neighbour_shape.next_no(touching_side_no_of_neighbour_room));
         PlaLineInt prev_line = room_shape.border_line(room_shape.prev_no(touching_side_no_of_room));
         if (prev_line.side_of(curr_first_corner) == PlaSide.ON_THE_RIGHT)
            precalculated_first_corner = curr_first_corner;
         else
            // curr_first_corner is outside the door shape
            precalculated_first_corner = room_shape.corner(touching_side_no_of_room);
         }

      return precalculated_first_corner;
      }

   /**
    * Returns the last corner of the intersection shape with the neighbour.
    */
   public PlaPointInt last_corner()
      {
      if (precalculated_last_corner != null) return precalculated_last_corner;

      if (room_touch_is_corner)
         {
         precalculated_last_corner = room_shape.corner(touching_side_no_of_room);
         }
      else if (neighbour_room_touch_is_corner)
         {
         precalculated_last_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
         }
      else
         {
         PlaPointInt curr_last_corner = neighbour_shape.corner(touching_side_no_of_neighbour_room);
         PlaLineInt next_line = room_shape.border_line(room_shape.next_no(touching_side_no_of_room));
         if (next_line.side_of(curr_last_corner) == PlaSide.ON_THE_RIGHT)
            precalculated_last_corner = curr_last_corner;
         else
            // curr_last_corner is outside the door shape
            precalculated_last_corner = room_shape.corner(room_shape.next_no(touching_side_no_of_room));
         }

      return precalculated_last_corner;
      }
   }
