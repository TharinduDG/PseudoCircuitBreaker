package utils

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Try

abstract class Settings[T](prefix: String) {

  def apply(config: Config): T = fromSubConfig(Try(config getConfig prefix).getOrElse(ConfigFactory.empty()))

  def fromSubConfig(c: Config): T
}