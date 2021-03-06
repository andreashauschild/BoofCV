/*
 * Copyright (c) 2011-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.struct;

import georegression.struct.point.Point3D_F64;

/**
 * 3D point with RGB stored in a compressed int format
 *
 * @author Peter Abeles
 */
public class Point3dRgbI extends Point3D_F64 {
	public int rgb;

	public Point3dRgbI( Point3D_F64 p , int rgb ) {
		this.set(p);
		this.rgb = rgb;
	}
}
