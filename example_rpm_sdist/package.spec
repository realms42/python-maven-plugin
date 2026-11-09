%global srcname example_package

Name:           python3-example-package
Version:        %{dist_rpm_version}
Release:        %{rpm_release}%{?dist}
Summary:        Example package built by python-maven-plugin
License:        GPL-3.0-only
URL:            https://github.com/realms42/python-maven-plugin
Source0:        %{dist_file}
BuildArch:      noarch

BuildRequires:  python3-devel
BuildRequires:  pyproject-rpm-macros

%description
The example package, built from its Maven-built sdist with Fedora's pyproject macros.

%prep
%autosetup -n %{srcname}-%{dist_version}

%generate_buildrequires
export SETUPTOOLS_SCM_PRETEND_VERSION=%{dist_version}
%pyproject_buildrequires

%build
export SETUPTOOLS_SCM_PRETEND_VERSION=%{dist_version}
%pyproject_wheel

%install
%pyproject_install
%pyproject_save_files -L %{srcname}

%check
%pyproject_check_import

%files -f %{pyproject_files}

%changelog
* Mon Nov 09 2026 realms42 - 1.0.0-1
- Build example_package from its sdist
