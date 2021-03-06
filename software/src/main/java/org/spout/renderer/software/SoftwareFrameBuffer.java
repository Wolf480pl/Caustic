/*
 * This file is part of Caustic Software.
 *
 * Copyright (c) 2013 Spout LLC <http://www.spout.org/>
 * Caustic Software is licensed under the Spout License Version 1.
 *
 * Caustic Software is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Caustic Software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.renderer.software;

import org.spout.renderer.api.gl.FrameBuffer;
import org.spout.renderer.api.gl.RenderBuffer;
import org.spout.renderer.api.gl.Texture;

/**
 *
 */
public class SoftwareFrameBuffer extends FrameBuffer {
    @Override
    public void bind() {

    }

    @Override
    public void unbind() {

    }

    @Override
    public void attach(AttachmentPoint point, Texture texture) {

    }

    @Override
    public void attach(AttachmentPoint point, RenderBuffer buffer) {

    }

    @Override
    public void detach(AttachmentPoint point) {

    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public GLVersion getGLVersion() {
        return GLVersion.SOFTWARE;
    }
}
