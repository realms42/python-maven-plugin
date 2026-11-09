%global srcname example_package
%global wheel %{_sourcedir}/%{srcname}-py3-none-any.whl
%global wheel_info %{python3} %{_sourcedir}/wheel_info.py

Name:           python3-example-package
Version:        %(%{wheel_info} rpm-version %{wheel})
Release:        1%{?dist}
Summary:        Example package built by python-maven-plugin
License:        GPL-3.0-only
URL:            https://github.com/realms42/python-maven-plugin
Source0:        %{srcname}-py3-none-any.whl
Source1:        wheel_info.py
BuildArch:      noarch

BuildRequires:  python3-devel
BuildRequires:  pyproject-rpm-macros
BuildRequires:  python3-pip

%description
The example package's wheel, as built by Maven, installed with Fedora's pyproject macros.

%prep
%setup -q -c -T

%build
mkdir -p %{_pyproject_wheeldir}
cp -p %{SOURCE0} %{_pyproject_wheeldir}/$(%{wheel_info} filename %{SOURCE0})

%install
%pyproject_install
%pyproject_save_files -L %{srcname}

%check
%pyproject_check_import

%files -f %{pyproject_files}
