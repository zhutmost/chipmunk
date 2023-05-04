package chipmunk
package sram

import scala.io.Source

/** Some helper functions for SRAM macro wrappers. */
private[sram] object SramUtils {
  def priorityInverse(p: SramPortPriority.Value): String = {
    if (p == SramPortPriority.Low) "~ " else ""
  }

  def templatePatternReplace(path: String, instance: String, sramConfig: SramConfig): String = {
    Source
      .fromResource(path)
      .mkString
      .replaceAll("\\$NAME", s"${sramConfig.name}")
      .replaceAll("\\$DEPTH", s"${sramConfig.depth}")
      .replaceAll("\\$DATA_WIDTH", s"${sramConfig.depth}")
      .replaceAll("\\$ADDR_WIDTH", s"${sramConfig.addrWidth}")
      .replaceAll("\\$MASK_WIDTH", s"${sramConfig.maskWidth}")
      .replaceAll("\\$MASK_UNIT", s"${sramConfig.maskUnit}")
      .replaceAll("\\$SRAM_MACRO_INSTANCE", instance)
  }
}
