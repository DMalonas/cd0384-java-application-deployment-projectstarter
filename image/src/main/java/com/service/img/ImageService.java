package com.service.img;

import java.awt.image.BufferedImage;

/**
 * @author DMalonas
 */
public interface ImageService {
    public boolean imageContainsCat(BufferedImage image, float confidenceThreshold);
}
