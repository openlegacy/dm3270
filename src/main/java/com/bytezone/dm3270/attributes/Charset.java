package com.bytezone.dm3270.attributes;

import com.bytezone.dm3270.display.ScreenContext;

public class Charset extends Attribute {

  private final boolean aplCharset;

  public Charset(byte charset) {
    super(AttributeType.CHARSET, Attribute.XA_CHARSET, charset);
    aplCharset = (charset == (byte) 0xf1);
  }

  @Override
  public ScreenContext process(ScreenContext defaultContext, ScreenContext currentContext) {
    return currentContext.withGraphic(aplCharset);
  }

}
