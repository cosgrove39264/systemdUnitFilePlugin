package net.sjrx.intellij.plugins.systemdunitfiles.filetypes;

import com.intellij.openapi.fileTypes.LanguageFileType;
import net.sjrx.intellij.plugins.systemdunitfiles.SystemdUnitFileIcon;
import net.sjrx.intellij.plugins.systemdunitfiles.SystemdUnitFileLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class ServiceFileType extends LanguageFileType {
  public static final ServiceFileType INSTANCE = new ServiceFileType();

  private ServiceFileType() {
    super(SystemdUnitFileLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "Service unit configuration for systemd";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "A unit configuration file whose name ends in \".service\" encodes information about a process controlled and supervised "
           + "by systemd.";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "service";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return SystemdUnitFileIcon.FILE;
  }
}
