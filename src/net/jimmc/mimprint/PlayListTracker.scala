/* PlayListTracker.scala
 *
 * Jim McBeath, June 10, 2008
 */

package net.jimmc.mimprint

import net.jimmc.util.ActorPublisher
import net.jimmc.util.AsyncUi
import net.jimmc.util.FileUtilS
import net.jimmc.util.PFCatch
import net.jimmc.util.SomeOrNone
import net.jimmc.util.StdLogger

import java.io.File;
import java.io.PrintWriter;

import scala.actors.Actor
import scala.actors.Actor.loop
import scala.collection.mutable.Map

/** A playlist of images. */
class PlayListTracker(val ui:AsyncUi) extends Actor
        with ActorPublisher[PlayListMessage]
        with StdLogger{
    //Our current playlist
    private var playList:PlayList = PlayList(ui)
    private var currentIndex:Int = -1
    private var isModified = false
    private var lastLoadFileName:String = _
    var askSaveOnChanges = false

    /* We always start our actor right away so that clients can send
     * us subscribe requests as soon as we are created.
     */
    this.start()

    def act() {
        loop {
            react (PFCatch(handleSubscribe orElse handleOther,
                    "PlayListTracker",ui))
        }
    }
    private val handleOther : PartialFunction[Any,Unit] = {
        case m:PlayListRequestInit =>
            m.sub ! PlayListInit(this,playList)
        case m:PlayListRequestAdd =>
            if (listMatches(m.list))
                addItem(m.item)
        case m:PlayListRequestInsert =>
            if (listMatches(m.list))
                insertItem(m.index,m.item)
        case m:PlayListRequestRemove =>
            if (listMatches(m.list))
                removeItem(m.index)
        case m:PlayListRequestChange =>
            if (listMatches(m.list))
                changeItem(m.index,m.item)
        case m:PlayListRequestUpdate =>
            if (listMatches(m.list))
                updateItem(m.index)
        case m:PlayListRequestSetItem =>
            if (listMatches(m.list))
                setItem(m.index,m.item)
        case m:PlayListRequestRotate =>
            if (listMatches(m.list))
                rotateItem(m.index, m.rot)
        case m:PlayListRequestSelect =>
            if (listMatches(m.list))
                selectItem(m.index)
        case m:PlayListRequestUp =>
            if (listMatches(m.list))
                selectUp()
        case m:PlayListRequestDown =>
            if (listMatches(m.list))
                selectDown()
        case m:PlayListRequestLeft =>
            if (listMatches(m.list))
                selectLeft()
        case m:PlayListRequestRight =>
            if (listMatches(m.list))
                selectRight()
        case _ => println("Unrecognized message to PlayList")
    }

    private def listMatches(list:PlayList):Boolean = {
        if (list!=playList) {
            println("Unknown or stale PlayList in tracker request")
                //Could happen, but should be rare, so we basically ignore it
        }
        (list==playList)          //OK to proceed if we have the right list
    }

    /** Add an item to our current PlayList to produce a new current PlayList,
     * publish notices about the change.
     */
    private def addItem(item:PlayItem) {
        val oldPlayList = playList
        val newPlayList = playList.addItem(item)
        val newIndex = playList.size - 1
        playList = newPlayList
        isModified = true
        publish(PlayListAddItem(this,oldPlayList,newPlayList,newIndex))
    }

    private def insertItem(itemIndex:Int, item:PlayItem) {
        val oldPlayList = playList
        val newPlayList = playList.insertItem(itemIndex, item)
        playList = newPlayList
        isModified = true
        publish(PlayListAddItem(this,oldPlayList,newPlayList,itemIndex))
    }

    private def removeItem(index:Int) {
        logger.debug("enter PlayListTracker.removeItem")
        val oldPlayList = playList
        val newPlayList = playList.removeItem(index)
        playList = newPlayList
        isModified = true
        publish(PlayListRemoveItem(this,oldPlayList,newPlayList,index))
        logger.debug("leave PlayListTracker.removeItem")
    }

    private def changeItem(itemIndex:Int, item:PlayItem) {
        val oldPlayList = playList
        val newPlayList = playList.replaceItem(itemIndex,item).
                asInstanceOf[PlayList]
        playList = newPlayList
        isModified = true
        publish(PlayListChangeItem(this,oldPlayList,newPlayList,itemIndex))
    }

    private def updateItem(itemIndex:Int) {
        publish(PlayListUpdateItem(this,playList,itemIndex))
    }

    private def setItem(itemIndex:Int, item:PlayItem) {
        val oldPlayList = playList
        val biggerPlayList = playList.ensureSize(itemIndex+1)
        val newPlayList = biggerPlayList.replaceItem(itemIndex,item).
                asInstanceOf[PlayList]
        playList = newPlayList
        isModified = true
        publish(PlayListChangeItem(this,oldPlayList,newPlayList,itemIndex))
    }

    private def rotateItem(itemIndex:Int, rot:Int) {
        val oldPlayList = playList
        val newPlayList = playList.rotateItem(itemIndex, rot).
                asInstanceOf[PlayList]
        playList = newPlayList
        isModified = true
        publish(PlayListChangeItem(this,oldPlayList,newPlayList,itemIndex))
    }

    private def selectItem(itemIndex:Int) {
        //no change to the playlist, we just publish a message
        currentIndex = itemIndex

        val pre = PlayListPreSelectItem(this,playList,itemIndex)

        logger.debug("PlayListTracker.selectItem publishing PreSelect")
        //First we publish a pre-select event so that everyone knows
        //we are about to select.  This should be handled quickly.
        publish(pre)
        //register at least one selector
        registerSelector(pre)

        logger.debug("PlayListTracker.selectItem publishing Select")
        //We publish the select event, which may take a while to process
        //(such as by the image viewer that has to load the image)
        publish(PlayListSelectItem(this,playList,itemIndex))

        //unregister; if nobody else registered, this will cause
        //the PostSelect to be sent; if sombody else registered,
        //the PostSelect will be sent only after they unregister.
        unregisterSelector(pre)

        logger.debug("PlayListTracker.selectItem done")
    }

    lazy val selectorMap = Map[PlayListPreSelectItem,Int]()

    //If a subscriber will take a long time to process the SelectItem message,
    //it can call this method when it gets a PreSelectItem, so that the
    //PostSelectItem will not be sent out until after it has called
    //unregisterSelector.
    def registerSelector(ev:PlayListPreSelectItem) {
        selectorMap.synchronized {
            val n = selectorMap.getOrElse(ev,0)
            logger.debug("PlayListTracker.registerSelector("+ev+")="+n)
            selectorMap.put(ev,n+1)
        }
    }

    def unregisterSelector(ev:PlayListPreSelectItem) {
        selectorMap.synchronized {
            val n = selectorMap.getOrElse(ev,0) - 1
            logger.debug("PlayListTracker.unregisterSelector("+ev+")="+n)
            if (n>0) {
                selectorMap.put(ev,n)
            } else {
                logger.debug("PlayListTracker.unregisterSelector publishing PostSelect")
                //Last we publish post-select event so that everyone knows
                //that the selection is done.  This should be handled quickly.
                selectorMap.put(ev,n - 1)
                selectorMap.remove(ev)
                if (n==0)
                    publish(PlayListPostSelectItem(this,ev.list,ev.index))
            }
        }
    }


    private def selectUp() {
        if (currentIndex>0)
            selectItem(currentIndex - 1)
        else {
            val prompt = "At beginning of "+playList.baseDir+";\n"
            val newDir:File = FileUtilS.getPreviousDirectory(playList.baseDir)
            if (newDir==null) {
                val eMsg = prompt + "No previous directory"
                ui.invokeUi(ui.errorDialog(eMsg))
            } else {
                val msg = prompt + "move to previous directory "+newDir+"?"
                val leftMsg = PlayListRequestLeft(playList)
                ui.invokeUi {
                    if (ui.confirmDialog(msg))
                        this ! leftMsg
                }
            }
        }
    }

    private def selectDown() {
        if (currentIndex< playList.size - 1)
            selectItem(currentIndex + 1)
        else {
            val prompt = "At end of "+playList.baseDir+";\n"
            val newDir:File = FileUtilS.getNextDirectory(playList.baseDir)
            if (newDir==null) {
                val eMsg = prompt + "No next directory"
                ui.invokeUi(ui.errorDialog(eMsg))
            } else {
                val msg = prompt + "move to next directory "+newDir+"?"
                val rightMsg = PlayListRequestRight(playList)
                ui.invokeUi {
                    if (ui.confirmDialog(msg))
                        this ! rightMsg
                }
            }
        }
    }

    private def selectLeft() {
        if (!saveChangesAndContinue())
            return      //canceled
        val newDir:File = FileUtilS.getPreviousDirectory(playList.baseDir)
        if (newDir==null) {
            val eMsg = "No previous directory"
            ui.invokeUi(ui.errorDialog(eMsg))
        } else {
            load(newDir.getPath,true)
        }
    }

    private def selectRight() {
        if (!saveChangesAndContinue())
            return      //canceled
        val newDir:File = FileUtilS.getNextDirectory(playList.baseDir)
        if (newDir==null) {
            val eMsg = "No next directory"
            ui.invokeUi(ui.errorDialog(eMsg))
        } else {
            load(newDir.getPath,false)
        }
    }

    ///So we can see what file we are dealing with
    def fileName = SomeOrNone(lastLoadFileName)

    ///Save our playlist to a file.
    def save(filename:String):Boolean = {
        val b =playList.save(filename)
        if (b) isModified = false
        b
    }

    def save(absolute:Boolean):Boolean = {
        save(lastLoadFileName,absolute)
    }

    def saveAs(defaultName:String, absolute:Boolean):Boolean = {
        val prompt = ui.getResourceString("dialog.PlayList.SaveAs.prompt")
        ui.fileSaveDialog(prompt,defaultName) match {
            case None => false
            case Some(f) => save(f,absolute)
        }
    }

    def save(filename:String,absolute:Boolean):Boolean = {
        val b = playList.save(filename,absolute)
        if (b) isModified = false
        b
    }

    def save(f:File):Boolean = save(f,false)

    def save(f:File, absolute:Boolean):Boolean = {
        val b = playList.save(f,absolute)
        if (b) isModified = false
        b
    }

    def save(out:PrintWriter, baseDir:File):Boolean = {
        val b = playList.save(out, baseDir)
        if (b) isModified = false
        b
    }

    def load(fileName:String):Unit = {
        if (!saveChangesAndContinue())
            return      //canceled
        load(fileName,false)
    }

    def load(fileName:String, selectLast:Boolean) {
        if (!saveChangesAndContinue())
            return      //canceled
        val oldPlayList = playList
        val newPlayList = PlayList.load(ui,fileName).asInstanceOf[PlayList]
        lastLoadFileName =
            if ((new File(fileName)).isDirectory) {
                if (fileName.endsWith(File.separator))
                    fileName+"index.mpr"        //don't double up the separator
                else
                    fileName+File.separator+"index.mpr"
            } else
                fileName
        playList = newPlayList
        isModified = false
        publish(PlayListChangeList(this,oldPlayList,newPlayList))
        val idx = if (selectLast) newPlayList.size - 1 else 0
        //Auto select the first/last item in the list if it is an image file
        if (newPlayList.size>0 &&
                FileInfo.isImageFileName(newPlayList.getItem(idx).fileName))
            selectItem(idx)
    }

    //If our playlist has changed AND the askSaveOnChanges flag is true,
    //we ask the user if he wants to save the playlist.
    //Return true if the user saved successfully or declined to save;
    //return false if the user canceled.
    def saveChangesAndContinue():Boolean = {
        if (!askSaveOnChanges)
            return true         //ignore changes at this point
        if (!isModified)
            return true         //no changes, no need to save
        val prefix = "dialog.PlayList.SaveChanges."
        val prompt = ui.getResourceString(prefix+"prompt")
            //TODO - put default filename into prompt
        val title = ui.getResourceString(prefix+"title")
        //val labels = ui.getResourceString(prefix+"buttons").split("\\|")
            //Labels are: Save, Save As, Discard, Cancel
        val buttonKeys = ui.getResourceString(prefix+"buttonKeys").split("\\|")
        ui.multiButtonDialogR(prompt, title, prefix, buttonKeys) match {
            case 0 =>   //Save to default location
                if (lastLoadFileName!=null)
                    save(lastLoadFileName)
                else
                    saveAs(null,false)
            case 1 =>   //Save As
                saveAs(lastLoadFileName,false)
            case 2 =>   //Ignore changes
                isModified = false      //we don't care about the changes
                true
            case 3 =>   //Cancel button
                false   //caller should not continue
            case -1 =>  //window close button, treat as cancel
                false
        }
    }
}
