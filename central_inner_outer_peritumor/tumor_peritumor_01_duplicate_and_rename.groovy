import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass
import qupath.lib.roi.GeometryTools
import qupath.lib.gui.dialogs.Dialogs

// === Najdi první anotaci v projektu ===
def annotations = getAnnotationObjects()
if (annotations.isEmpty()) {
    Dialogs.showErrorMessage("Error", "❌ No annotations found! Draw a region first.")
    return
}

// Vezmi první anotaci (např. pořadí podle času vytvoření)
def firstAnno = annotations[0]
print "✅ Found first annotation: ${firstAnno.getName()}"

// === Nastav název a třídu ===
firstAnno.setName("Tumor")
firstAnno.setPathClass(PathClass.fromString("Tumor"))
print "✅ Set first annotation name='Tumor', class='Tumor'"

// === Duplikuj ji jako 'Sample' ===
def geomTumor = GeometryTools.roiToGeometry(firstAnno.getROI())
def roiSample = GeometryTools.geometryToROI(geomTumor, firstAnno.getROI().getImagePlane())
def annSample = PathObjects.createAnnotationObject(roiSample, PathClass.fromString("Other"))
annSample.setName("Sample")
addObject(annSample)
print "✅ Duplicated 'Tumor' → new annotation 'sample'"
