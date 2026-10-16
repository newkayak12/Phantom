import AppKit
// usage: compose <mode> <src.png> <out.png> <size>   mode: icon | head | splash
let a = CommandLine.arguments
let mode = a[1], src = NSImage(contentsOfFile: a[2])!, out = a[3], size = Int(a[4])!
var rect = NSRect(x: 0, y: 0, width: 1, height: 1)
guard let cg = src.cgImage(forProposedRect: &rect, context: nil, hints: nil) else { fatalError("load") }
let W = cg.width, H = cg.height
func bgColor() -> CGColor {
  let rep = NSBitmapImageRep(cgImage: cg); return (rep.colorAt(x: 8, y: 8) ?? .lightGray).cgColor
}
// draw the full image fitted to height, then fade its left/right edges into the background color
func drawFaded(_ ctx: CGContext, _ r: CGRect) {
  ctx.draw(cg, in: r)
  let fade = r.width * 0.12
  let cs = CGColorSpace(name: CGColorSpace.sRGB)!
  let c = bgColor(), clear = c.copy(alpha: 0)!
  let g = CGGradient(colorsSpace: cs, colors: [c, clear] as CFArray, locations: [0, 1])!
  ctx.saveGState(); ctx.clip(to: CGRect(x: r.minX, y: r.minY, width: fade, height: r.height))
  ctx.drawLinearGradient(g, start: CGPoint(x: r.minX, y: 0), end: CGPoint(x: r.minX + fade, y: 0), options: []); ctx.restoreGState()
  ctx.saveGState(); ctx.clip(to: CGRect(x: r.maxX - fade, y: r.minY, width: fade, height: r.height))
  ctx.drawLinearGradient(g, start: CGPoint(x: r.maxX, y: 0), end: CGPoint(x: r.maxX - fade, y: 0), options: []); ctx.restoreGState()
}
func render(_ w: Int, _ h: Int, _ draw: (CGContext) -> Void) {
  let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                      space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
  ctx.interpolationQuality = .high
  draw(ctx)
  let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
  try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
}
switch mode {
case "icon", "head":
  // icon: square crop of the whole character; head: hood + eyes only (for 16/32 px)
  let crop = mode == "icon" ? CGRect(x: 0, y: (H - W) / 2 + 40, width: W, height: W)
                            : CGRect(x: 300, y: 10, width: 720, height: 720)
  let piece = cg.cropping(to: crop)!
  render(size, size) { ctx in
    let s = CGFloat(size), inset = mode == "icon" ? s * 0.098 : 0, body = s - inset * 2
    let r = body * 0.225
    let path = CGPath(roundedRect: CGRect(x: inset, y: inset, width: body, height: body), cornerWidth: r, cornerHeight: r, transform: nil)
    ctx.addPath(path); ctx.clip()
    ctx.setFillColor(bgColor()); ctx.fill(CGRect(x: 0, y: 0, width: s, height: s))
    if mode == "icon" {
      let h = body * 0.96, w = h * CGFloat(W) / CGFloat(H)
      drawFaded(ctx, CGRect(x: inset + (body - w) / 2, y: inset + (body - h) / 2, width: w, height: h))
    } else {
      ctx.draw(piece, in: CGRect(x: inset, y: inset, width: body, height: body))
    }
  }
case "splash":
  let w = size, h = size * 400 / 640, k = CGFloat(size) / 640
  render(w, h) { ctx in
    ctx.setFillColor(bgColor()); ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
    let ch = CGFloat(h) * 0.98, cw = ch * CGFloat(W) / CGFloat(H)
    drawFaded(ctx, CGRect(x: CGFloat(w) - cw - 4 * k, y: (CGFloat(h) - ch) / 2, width: cw, height: ch))
    let ns = NSGraphicsContext(cgContext: ctx, flipped: false)
    NSGraphicsContext.current = ns
    func text(_ t: String, _ y: CGFloat, _ sz: CGFloat, _ weight: NSFont.Weight, _ c: NSColor) {
      NSAttributedString(string: t, attributes: [.font: NSFont.systemFont(ofSize: sz * k, weight: weight), .foregroundColor: c])
        .draw(at: NSPoint(x: 48 * k, y: y * k))
    }
    text("Phantom IDEA", 176, 46, .bold, NSColor(white: 0.07, alpha: 1))
    text("A lighter IDE on the IntelliJ Platform", 146, 16, .regular, NSColor(white: 0.30, alpha: 1))
    text("Built from JetBrains/intellij-community (Apache 2.0)", 32, 11, .regular, NSColor(white: 0.45, alpha: 1))
  }
default: fatalError("mode")
}
