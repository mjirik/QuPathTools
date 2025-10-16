import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import java.awt.Color
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.objects.classes.PathClass


import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate


import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.util.AffineTransformation

/**
 * Vrátí geometrii uvnitř obrázku zmenšenou o margin_um od všech okrajů.
 * Správně ošetří izotropní i anizotropní pixely.
 */
def getInnerImageGeometry(double margin_um) {
    def imageData = getCurrentImageData()
    if (imageData == null) {
        print "❌ No image open!"
        return null
    }

    def server = imageData.getServer()
    def cal = server.getPixelCalibration()
    double w_px = server.getWidth()
    double h_px = server.getHeight()
    double sx = cal.getPixelWidthMicrons()
    double sy = cal.getPixelHeightMicrons()

    if (!(sx>0) || !(sy>0)) {
        print "⚠️ Calibration of pixels is not valid (sx=${sx}, sy=${sy}) – using 1 µm/px."
        sx = 1.0; sy = 1.0
    }

    def gf = new GeometryFactory()
    Geometry rectPx = gf.createPolygon([
        new Coordinate(0,     0),
        new Coordinate(w_px,  0),
        new Coordinate(w_px,  h_px),
        new Coordinate(0,     h_px),
        new Coordinate(0,     0)
    ] as Coordinate[])

    Geometry innerPx
    if (Math.abs(sx - sy) < 1e-9) {
        // izotropní pixely → převedeme margin na pixely a bufferujeme v pixelech
        double margin_px = margin_um / sx
        innerPx = rectPx.buffer(-margin_px)
    } else {
        // anizotropie → transformace do µm, buffer v µm, transformace zpět do px
        def toMic = new AffineTransformation().scale(sx, sy)
        def toPix = new AffineTransformation().scale(1.0/sx, 1.0/sy)
        Geometry rectUm = toMic.transform(rectPx)
        Geometry innerUm = rectUm.buffer(-margin_um)
        innerPx = toPix.transform(innerUm)
    }

    if (innerPx.isEmpty()) {
        print "⚠️ Margin ${margin_um} µm is to high. No space for mask left inside the image."
        return null
    }

    print "✅ Inner mask OK (margin=${margin_um} µm; sx=${sx} µm/px; sy=${sy} µm/px)."
    return innerPx
}


def addAnnotationFromGeometry(geom, name, pathClassName="Default", color=null, imagePlane=null) {
    if (geom == null || geom.isEmpty()) {
        print "⚠️ Geometry '${name}' is empty, skipping."
        return null
    }

    if (imagePlane == null) {
        print "⚠️ No image plane provided for '${name}', using default (Z=0, T=0)."
        imagePlane = qupath.lib.regions.ImagePlane.getDefaultPlane()
    }

    def roi = GeometryTools.geometryToROI(geom, imagePlane)
    def pathClass = PathClass.fromString(pathClassName)
    if (color != null)
        pathClass.setColor(color.getRGB())


    def ann = PathObjects.createAnnotationObject(roi, pathClass)
    ann.setName(name)
    addObject(ann)
    print "✅ Added annotation '${name}' (class '${pathClassName}')"
    return ann
}



////////////////////////////////////
// spustí stejný algoritmus jako GUI -> Classify › Create thresholder…
//runCommand('Create thresholder', [
//    'method'           : 'Otsu',         // nebo 'Fixed', 'Mean', 'Triangle'…
//    'measurement'      : 'Brightness',   // nebo "Optical density sum"
//    'threshold'        : 180,            // používá se jen u "Fixed"
//    'downsample'       : 1.0,
//    'includeParent'    : false,          // false = celý obraz
//    'addAnnotations'   : true,           // vytvoří anotaci
//    'splitAnnotations' : false           // všechno jako jedna oblast
//])

/////////////////////////////////////


// === Najdi anotace ===
def annotations = getAnnotationObjects()
if (annotations.isEmpty()) {
    print "❌ No annotations found!"
    return
}

// === Najdi Tumor (podle jména nebo třídy) ===
def annTumor = annotations.find { 
    it.getName()?.equalsIgnoreCase("Tumor") ||
    it.getName()?.equalsIgnoreCase("tumor") ||
    it.getPathClass()?.getName()?.equalsIgnoreCase("Tumor")
}

if (!annTumor) {
    print "⚠️ No annotation named or classified as 'Tumor' found."
}

// === Najdi Sample ===
def annSample = annotations.find { 
    it.getName()?.equalsIgnoreCase("Sample") ||
    it.getPathClass()?.getName()?.equalsIgnoreCase("Sample")
}

// === Pokud Sample nenajdeme, ale máme jen dvě anotace ===
if (!annSample && annotations.size() == 2 && annTumor) {
    annSample = annotations.find { it != annTumor }
    print "ℹ️ Using the other annotation as 'Sample'."
}

// === Kontrola výsledků ===
if (!annTumor || !annSample) {
    print "❌ Could not find both 'Tumor' and 'Sample' annotations."
    print "   Found: Tumor=${annTumor != null}, Sample=${annSample != null}"
    return
}

print "✅ Tumor annotation: ${annTumor.getName()} (${annTumor.getPathClass()?.getName()})"
print "✅ Sample annotation: ${annSample.getName()} (${annSample.getPathClass()?.getName()})"


/*
// === Check the requred annotations ===
def annotations = getAnnotationObjects()
def annSample = annotations.find { it.getName() == 'sample' }
def segPolygon = annotations.find { it.getName() == 'Tumor' }

if (!annSample) {
    print "❌ Annotation 'sample' not found"
    Dialogs.showErrorMessage("Error", "Annotation 'sample' not found")
    return
}
if (!segPolygon) {
    print "❌ Annotation 'selection' not found"
    Dialogs.showErrorMessage("Error", "Annotation selection' not found")
    return
}


*/



def imagePlane = annSample.getROI().getImagePlane()



// === margin dialog ===
def input = Dialogs.showInputDialog(
    "Size of margin",
    "Margin in µm:",
    "500"
)
if (input == null) {
    print "Canceled by user."
    return
}
double margin_um
try {
    margin_um = Double.parseDouble(input)
} catch (Exception e) {
    Dialogs.showErrorMessage("Error", "Not a number: '${input}'")
    return
}

def cal = getCurrentImageData().getServer().getPixelCalibration()
double pxWidth = cal.getPixelWidthMicrons()   // µm / pixel
double pxHeight = cal.getPixelHeightMicrons()

// if pixels are not square, take the mean
double pxSize = (pxWidth + pxHeight) / 2.0

double margin_px = margin_um / pxSize


def geomInnerImage = getInnerImageGeometry(margin_um * 0.25)

// === Turn polyline into JTS geometry ===
def roiLine = segPolygon.getROI()
def coords = roiLine.getAllPoints().collect { p -> new Coordinate(p.getX(), p.getY()) }
def factory = new GeometryFactory()
def geomSeg = factory.createLineString(coords as Coordinate[]).intersection(geomInnerImage)

// === Make a polyline wider  ===
def bufferedSeg = geomSeg.buffer(margin_px)  // šířka pásu v µm

def geomSample = GeometryTools.roiToGeometry(annSample.getROI())

// === Intersection ===
def geomIntersect = geomSample.intersection(bufferedSeg).intersection(geomInnerImage)

if (geomIntersect.isEmpty()) {
    print "⚠ No intersection. Check if the 'selection' has intersection with 'tissue'."
    return
}


def roiIntersect = GeometryTools.geometryToROI(geomIntersect, annSample.getROI().getImagePlane())
// def newAnno = PathObjects.createAnnotationObject(roiIntersect, annSample.getPathClass())
// newAnno.setName("_" + annSample.getName() + "_intersection")

// addObject(newAnno)






// === Create "inner" and "outer" boundary ===
def inner = geomSample.buffer(-margin_px)  // zmenšení o margin
def ring = geomSample.difference(inner) // rozdíl = okrajový prstenec

if (ring.isEmpty()) {
    Dialogs.showErrorMessage("No margin", "⚠️ Cannot create margin area. The margin might be to high.")
    return
}

// === Inner margin annotation ===
def roiRing = GeometryTools.geometryToROI(ring, annSample.getROI().getImagePlane())
//def newAnnoTissueMargin = PathObjects.createAnnotationObject(roiRing, annSample.getPathClass())
//newAnnoTissueMargin.setName("_" + annSample.getName() + "_sample_margin_" + margin.intValue() + "µm")

//addObject(newAnnoTissueMargin)



// Make central selected part
def geomSelection = GeometryTools.roiToGeometry(segPolygon.getROI())
def geomCentral = inner.intersection(geomSelection).difference(geomIntersect)


def roiCentral = GeometryTools.geometryToROI(geomCentral, annSample.getROI().getImagePlane())


// def annCentral = PathObjects.createAnnotationObject(roiCentral, annSample.getPathClass())
// annCentral.setName("_" + annSample.getName() + "_central")
// // přiřazení třídy

// annCentral.setPathClass(PathClass.fromString("Tumor"))

// addObject(annCentral)


// Make outer part
geomOuterAll = geomSample.difference(geomSelection)

geomOuterAllExtended = geomOuterAll.buffer(margin_px) // this is to remove small segmentations on the boundary of the image

geomInnerMargin = geomIntersect.intersection(geomSelection).intersection(geomOuterAllExtended)
geomOuterMargin = geomIntersect.intersection(geomOuterAll)
geomPeritumor = geomOuterAll.difference(geomIntersect)


addAnnotationFromGeometry(geomCentral, "Central", "Tumor", Color.RED, imagePlane)
addAnnotationFromGeometry(geomInnerMargin, "Inner margin", "Tumor", Color.GREEN, imagePlane)

addAnnotationFromGeometry(geomOuterMargin, "Outer_margin", "Other", Color.BLUE, imagePlane)
addAnnotationFromGeometry(geomPeritumor, "Peritumor", "Other", Color.ORANGE, imagePlane)


// addAnnotationFromGeometry(geomSample, "__Sample", "Other", Color.GREEN, imagePlane)
// addAnnotationFromGeometry(geomIntersect, "___intersect", "Other", Color.ORANGE, imagePlane)
// addAnnotationFromGeometry(geomInnerImage, "__ inner image", "Other", Color.ORANGE, imagePlane)


Dialogs.showInfoNotification("Finished", "Central, Inner, Outer and Peritumor annotation created with inner margin ${margin_um} µm.")


